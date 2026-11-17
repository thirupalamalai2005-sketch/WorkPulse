package com.workpulse.service;

import com.workpulse.dto.LeaveDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.*;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.LeaveRequestRepository;
import com.workpulse.repository.LeaveTypeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * Leave business rules (documented in README):
 *  - days are WORKING days of the company schedule (weekends/holidays excluded)
 *  - a request must lie inside one calendar year and cannot overlap another PENDING/APPROVED request
 *  - remaining = yearly entitlement - APPROVED days - PENDING days (pending days are reserved)
 *  - only a manager/admin who is allowed to review the employee can approve/reject; never your own request
 *  - owners can cancel PENDING requests, and APPROVED ones that have not started yet
 */
@Service
public class LeaveService {

    static final int MAX_BACKDATE_DAYS = 30;
    private static final List<LeaveStatus> ACTIVE_STATUSES = List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository leaveRepository;
    private final LeaveTypeRepository typeRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleService scheduleService;
    private final AccessService access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public LeaveService(LeaveRequestRepository leaveRepository, LeaveTypeRepository typeRepository,
                        EmployeeRepository employeeRepository, ScheduleService scheduleService,
                        AccessService access, NotificationService notifications, AuditService audit, Clock clock) {
        this.leaveRepository = leaveRepository;
        this.typeRepository = typeRepository;
        this.employeeRepository = employeeRepository;
        this.scheduleService = scheduleService;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- leave types

    @Transactional(readOnly = true)
    public List<LeaveDtos.TypeResponse> types(boolean includeInactive) {
        List<LeaveType> list = includeInactive ? typeRepository.findAll(Sort.by("name")) : typeRepository.findByActiveTrueOrderByName();
        return list.stream().map(LeaveService::toTypeResponse).toList();
    }

    @Transactional
    public LeaveDtos.TypeResponse updateType(Long id, LeaveDtos.TypeUpdateRequest req) {
        LeaveType t = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        t.setName(req.name().trim());
        t.setAnnualEntitlement(req.annualEntitlement());
        t.setAllowPastDates(req.allowPastDates());
        t.setActive(req.active());
        typeRepository.save(t);
        audit.logCurrent("LEAVE_TYPE_UPDATED", "LEAVE_TYPE", t.getId(),
                "code=" + t.getCode() + ", entitlement=" + t.getAnnualEntitlement() + ", active=" + t.isActive());
        return toTypeResponse(t);
    }

    // ---------------------------------------------------------------- balances

    @Transactional(readOnly = true)
    public List<LeaveDtos.Balance> balances(Long employeeId, Integer year) {
        Employee me = access.currentEmployee();
        Employee target = me;
        if (employeeId != null && !employeeId.equals(me.getId())) {
            target = employeeRepository.findById(employeeId).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
            access.requireCanViewPrivate(target);
        }
        return balancesFor(target.getId(), year != null ? year : LocalDate.now(clock).getYear());
    }

    List<LeaveDtos.Balance> balancesFor(Long employeeId, int year) {
        Map<Long, int[]> sums = sumsFor(employeeId, year); // typeId -> [approved, pending]
        List<LeaveDtos.Balance> result = new ArrayList<>();
        for (LeaveType t : typeRepository.findByActiveTrueOrderByName()) {
            int[] s = sums.getOrDefault(t.getId(), new int[]{0, 0});
            result.add(new LeaveDtos.Balance(t.getId(), t.getName(), t.getAnnualEntitlement(), s[0], s[1],
                    t.getAnnualEntitlement() - s[0] - s[1]));
        }
        return result;
    }

    private Map<Long, int[]> sumsFor(Long employeeId, int year) {
        Map<Long, int[]> sums = new HashMap<>();
        for (Object[] row : leaveRepository.sumDaysByTypeAndStatus(employeeId, LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31), ACTIVE_STATUSES)) {
            int[] s = sums.computeIfAbsent((Long) row[0], k -> new int[2]);
            int days = ((Number) row[2]).intValue();
            if (row[1] == LeaveStatus.APPROVED) s[0] += days; else s[1] += days;
        }
        return sums;
    }

    // ---------------------------------------------------------------- requests

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LeaveDtos.Response submit(LeaveDtos.SubmitRequest req) {
        Employee me = access.currentEmployee();
        // Acquire row-level write lock on the submitting employee to serialize leave
        // submissions for this employee under READ_COMMITTED. This prevents concurrent
        // submissions from overdrawing the balance or creating overlapping requests.
        employeeRepository.findByIdForUpdate(me.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        LeaveType type = typeRepository.findById(req.leaveTypeId())
                .orElseThrow(() -> new BadRequestException("Leave type does not exist"));
        if (!type.isActive()) throw new BadRequestException("This leave type is not available");
        LocalDate start = req.startDate(), end = req.endDate(), today = LocalDate.now(clock);

        if (end.isBefore(start)) throw new BadRequestException("End date cannot be before start date");
        if (start.getYear() != end.getYear()) {
            throw new BadRequestException("A request cannot span two calendar years; submit one request per year");
        }
        LocalDate earliest = type.isAllowPastDates() ? today.minusDays(MAX_BACKDATE_DAYS) : today;
        if (start.isBefore(earliest)) {
            throw new BadRequestException(type.isAllowPastDates()
                    ? "Start date cannot be more than " + MAX_BACKDATE_DAYS + " days in the past"
                    : "Start date cannot be in the past for this leave type");
        }
        int days = scheduleService.workingDaysBetween(start, end).size();
        if (days == 0) throw new BadRequestException("The selected dates contain no working days");

        if (leaveRepository.existsOverlap(me.getId(), start, end, ACTIVE_STATUSES)) {
            throw new ConflictException("You already have a pending or approved request overlapping these dates");
        }
        int[] used = sumsFor(me.getId(), start.getYear()).getOrDefault(type.getId(), new int[]{0, 0});
        int remaining = type.getAnnualEntitlement() - used[0] - used[1];
        if (days > remaining) {
            throw new BadRequestException("Insufficient " + type.getName() + " balance: requested " + days
                    + " day(s), remaining " + Math.max(remaining, 0));
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(me);
        lr.setLeaveType(type);
        lr.setStartDate(start);
        lr.setEndDate(end);
        lr.setDayCount(days);
        lr.setReason(req.reason().trim());
        lr = leaveRepository.save(lr);

        for (User approver : access.approversFor(me)) {
            notifications.notifyUser(approver.getUserId(), "LEAVE", "New leave request",
                    me.getUser().getUsername() + " requested " + days + " day(s) of " + type.getName()
                            + " (" + start + " to " + end + ")", "/leaves.html");
        }
        audit.logCurrent("LEAVE_SUBMITTED", "LEAVE", lr.getId(),
                "type=" + type.getCode() + ", " + start + " to " + end + ", days=" + days);
        return toResponse(lr, me.getId());
    }

    @Transactional
    public LeaveDtos.Response approve(Long id, String note) {
        LeaveRequest lr = loadForReview(id);
        decide(lr, LeaveStatus.APPROVED, note);
        notifications.notifyUser(lr.getEmployee().getUser().getUserId(), "LEAVE", "Leave request approved",
                "Your " + lr.getLeaveType().getName() + " request (" + lr.getStartDate() + " to " + lr.getEndDate() + ") was approved.",
                "/leaves.html");
        audit.logCurrent("LEAVE_APPROVED", "LEAVE", lr.getId(),
                "employee=" + lr.getEmployee().getEmployeeCode() + ", " + lr.getStartDate() + " to " + lr.getEndDate());
        return toResponse(lr, access.currentEmployee().getId());
    }

    @Transactional
    public LeaveDtos.Response reject(Long id, String note) {
        if (note == null || note.isBlank()) throw new BadRequestException("A note is required when rejecting");
        LeaveRequest lr = loadForReview(id);
        decide(lr, LeaveStatus.REJECTED, note);
        notifications.notifyUser(lr.getEmployee().getUser().getUserId(), "LEAVE", "Leave request rejected",
                "Your " + lr.getLeaveType().getName() + " request (" + lr.getStartDate() + " to " + lr.getEndDate() + ") was rejected.",
                "/leaves.html");
        audit.logCurrent("LEAVE_REJECTED", "LEAVE", lr.getId(),
                "employee=" + lr.getEmployee().getEmployeeCode() + ", " + lr.getStartDate() + " to " + lr.getEndDate());
        return toResponse(lr, access.currentEmployee().getId());
    }

    @Transactional
    public LeaveDtos.Response cancel(Long id) {
        Employee me = access.currentEmployee();
        LeaveRequest lr = leaveRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        if (!lr.getEmployee().getId().equals(me.getId())) {
            throw new AccessDeniedException("You can only cancel your own requests");
        }
        LocalDate today = LocalDate.now(clock);
        boolean wasApproved = lr.getStatus() == LeaveStatus.APPROVED;
        if (lr.getStatus() == LeaveStatus.PENDING) {
            // always cancellable
        } else if (wasApproved) {
            if (!lr.getStartDate().isAfter(today)) {
                throw new ConflictException("An approved leave that has already started cannot be cancelled");
            }
        } else {
            throw new ConflictException("Only pending or upcoming approved requests can be cancelled");
        }
        lr.setStatus(LeaveStatus.CANCELLED);
        lr.setCancelledAt(clock.instant());
        leaveRepository.save(lr);
        if (wasApproved) {
            for (User approver : access.approversFor(me)) {
                notifications.notifyUser(approver.getUserId(), "LEAVE", "Approved leave cancelled",
                        me.getUser().getUsername() + " cancelled approved leave (" + lr.getStartDate() + " to " + lr.getEndDate() + ")",
                        "/leaves.html");
            }
        }
        audit.logCurrent("LEAVE_CANCELLED", "LEAVE", lr.getId(), "wasApproved=" + wasApproved);
        return toResponse(lr, me.getId());
    }

    @Transactional(readOnly = true)
    public LeaveDtos.Response get(Long id) {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        LeaveRequest lr = leaveRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        boolean own = lr.getEmployee().getId().equals(me.getId());
        if (!own && !access.canReview(viewer, me, lr.getEmployee())) {
            throw new AccessDeniedException("You may not view this leave request");
        }
        return toResponse(lr, me.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveDtos.Response> mine(LeaveStatus status, int page, int size) {
        Employee me = access.currentEmployee();
        Specification<LeaveRequest> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("employee").get("id"), me.getId()));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(leaveRepository.findAll(spec, pageable(page, size)), l -> toResponse(l, me.getId()));
    }

    /** Requests the caller may decide on: admin = everyone else's, manager = direct reports'. */
    @Transactional(readOnly = true)
    public PageResponse<LeaveDtos.Response> forReview(LeaveStatus status, int page, int size) {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        Specification<LeaveRequest> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            p.add(cb.notEqual(root.get("employee").get("id"), me.getId()));
            if (!admin) p.add(cb.equal(root.get("employee").get("manager").get("id"), me.getId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(leaveRepository.findAll(spec, pageable(page, size)), l -> toResponse(l, me.getId()));
    }

    // ---------------------------------------------------------------- helpers

    private LeaveRequest loadForReview(Long id) {
        LeaveRequest lr = leaveRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        User reviewer = access.currentUser();
        Employee reviewerEmp = access.currentEmployee();
        if (!access.canReview(reviewer, reviewerEmp, lr.getEmployee())) {
            throw new AccessDeniedException("You may not review this request");
        }
        if (lr.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("This request is already " + lr.getStatus().name().toLowerCase());
        }
        return lr;
    }

    private void decide(LeaveRequest lr, LeaveStatus status, String note) {
        lr.setStatus(status);
        lr.setReviewer(access.currentEmployee());
        lr.setReviewedAt(clock.instant());
        lr.setReviewNote(UserProvisioningService.blankToNull(note));
        leaveRepository.save(lr);
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), AuditService.clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    LeaveDtos.Response toResponse(LeaveRequest l, Long viewerEmployeeId) {
        Employee e = l.getEmployee();
        LocalDate today = LocalDate.now(clock);
        boolean cancellable = e.getId().equals(viewerEmployeeId)
                && (l.getStatus() == LeaveStatus.PENDING
                    || (l.getStatus() == LeaveStatus.APPROVED && l.getStartDate().isAfter(today)));
        return new LeaveDtos.Response(l.getId(), e.getId(), e.getEmployeeCode(), e.getUser().getUsername(),
                l.getLeaveType().getId(), l.getLeaveType().getName(), l.getStartDate(), l.getEndDate(), l.getDayCount(),
                l.getReason(), l.getStatus().name(),
                l.getReviewer() == null ? null : l.getReviewer().getUser().getUsername(),
                l.getReviewedAt(), l.getReviewNote(), l.getCreatedAt(), cancellable);
    }

    private static LeaveDtos.TypeResponse toTypeResponse(LeaveType t) {
        return new LeaveDtos.TypeResponse(t.getId(), t.getCode(), t.getName(), t.getAnnualEntitlement(),
                t.isAllowPastDates(), t.isActive());
    }
}
