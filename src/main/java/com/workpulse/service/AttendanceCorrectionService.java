package com.workpulse.service;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.*;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.AttendanceCorrectionRepository;
import com.workpulse.repository.AttendanceRecordRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/** Employees request a fix for a day's check-in/out times; a manager/admin approves or rejects it. */
@Service
public class AttendanceCorrectionService {

    static final int MAX_AGE_DAYS = 30;

    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceRecordRepository recordRepository;
    private final ScheduleService scheduleService;
    private final AccessService access;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public AttendanceCorrectionService(AttendanceCorrectionRepository correctionRepository,
                                       AttendanceRecordRepository recordRepository, ScheduleService scheduleService,
                                       AccessService access, NotificationService notifications,
                                       AuditService audit, Clock clock) {
        this.correctionRepository = correctionRepository;
        this.recordRepository = recordRepository;
        this.scheduleService = scheduleService;
        this.access = access;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AttendanceDtos.CorrectionResponse submit(AttendanceDtos.CorrectionRequest req) {
        Employee me = access.currentEmployee();
        LocalDate today = LocalDate.now(clock);
        if (req.workDate().isAfter(today)) throw new BadRequestException("Work date cannot be in the future");
        if (req.workDate().isBefore(today.minusDays(MAX_AGE_DAYS))) {
            throw new BadRequestException("Corrections can only be requested for the last " + MAX_AGE_DAYS + " days");
        }
        if (req.checkInTime() == null && req.checkOutTime() == null) {
            throw new BadRequestException("Provide a check-in time, a check-out time, or both");
        }
        if (req.checkInTime() != null && req.checkOutTime() != null && !req.checkInTime().isBefore(req.checkOutTime())) {
            throw new BadRequestException("Check-out time must be after check-in time");
        }
        boolean hasRecord = recordRepository.findByEmployee_IdAndWorkDate(me.getId(), req.workDate()).isPresent();
        if (!hasRecord && req.checkInTime() == null) {
            throw new BadRequestException("No attendance exists for that day, so a check-in time is required");
        }
        if (correctionRepository.existsByEmployee_IdAndWorkDateAndStatus(me.getId(), req.workDate(), CorrectionStatus.PENDING)) {
            throw new ConflictException("A correction for that day is already pending");
        }
        AttendanceCorrection c = new AttendanceCorrection();
        c.setEmployee(me);
        c.setWorkDate(req.workDate());
        c.setRequestedCheckIn(toInstant(req.workDate(), req.checkInTime()));
        c.setRequestedCheckOut(toInstant(req.workDate(), req.checkOutTime()));
        c.setReason(req.reason().trim());
        c = correctionRepository.save(c);

        for (User approver : access.approversFor(me)) {
            notifications.notifyUser(approver.getUserId(), "CORRECTION", "Attendance correction requested",
                    me.getUser().getUsername() + " requested a correction for " + req.workDate(), "/attendance.html");
        }
        audit.logCurrent("CORRECTION_SUBMITTED", "ATTENDANCE_CORRECTION", c.getId(), "date=" + req.workDate());
        return toResponse(c);
    }

    @Transactional
    public AttendanceDtos.CorrectionResponse approve(Long id, String note) {
        AttendanceCorrection c = loadPendingReviewable(id);
        Employee subject = c.getEmployee();
        AttendanceRecord rec = recordRepository.findByEmployee_IdAndWorkDate(subject.getId(), c.getWorkDate()).orElse(null);

        Instant in = c.getRequestedCheckIn() != null ? c.getRequestedCheckIn() : (rec != null ? rec.getCheckInAt() : null);
        Instant out = c.getRequestedCheckOut() != null ? c.getRequestedCheckOut() : (rec != null ? rec.getCheckOutAt() : null);
        if (in == null) throw new BadRequestException("No check-in time available for this correction");
        if (out != null && !out.isAfter(in)) throw new BadRequestException("Check-out must be after check-in");

        if (rec == null) {
            rec = new AttendanceRecord();
            rec.setEmployee(subject);
            rec.setWorkDate(c.getWorkDate());
        }
        rec.setCheckInAt(in);
        rec.setCheckOutAt(out);
        rec.setLate(scheduleService.isLate(in, c.getWorkDate(), scheduleService.current()));
        rec.setSource(AttendanceRecord.SOURCE_CORRECTION);
        recordRepository.save(rec);

        finish(c, CorrectionStatus.APPROVED, note);
        notifications.notifyUser(subject.getUser().getUserId(), "CORRECTION", "Attendance correction approved",
                "Your correction for " + c.getWorkDate() + " was approved.", "/attendance.html");
        audit.logCurrent("CORRECTION_APPROVED", "ATTENDANCE_CORRECTION", c.getId(),
                "employee=" + subject.getEmployeeCode() + ", date=" + c.getWorkDate());
        return toResponse(c);
    }

    @Transactional
    public AttendanceDtos.CorrectionResponse reject(Long id, String note) {
        if (note == null || note.isBlank()) throw new BadRequestException("A note is required when rejecting");
        AttendanceCorrection c = loadPendingReviewable(id);
        finish(c, CorrectionStatus.REJECTED, note);
        notifications.notifyUser(c.getEmployee().getUser().getUserId(), "CORRECTION", "Attendance correction rejected",
                "Your correction for " + c.getWorkDate() + " was rejected.", "/attendance.html");
        audit.logCurrent("CORRECTION_REJECTED", "ATTENDANCE_CORRECTION", c.getId(),
                "employee=" + c.getEmployee().getEmployeeCode() + ", date=" + c.getWorkDate());
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.CorrectionResponse> mine(int page, int size) {
        Employee me = access.currentEmployee();
        Specification<AttendanceCorrection> spec = (root, q, cb) -> cb.equal(root.get("employee").get("id"), me.getId());
        return PageResponse.of(correctionRepository.findAll(spec, pageable(page, size)), this::toResponse);
    }

    /** Requests the caller may review: admin = everyone else's, manager = direct reports'. */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.CorrectionResponse> forReview(CorrectionStatus status, int page, int size) {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        Specification<AttendanceCorrection> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            p.add(cb.notEqual(root.get("employee").get("id"), me.getId()));
            if (!admin) p.add(cb.equal(root.get("employee").get("manager").get("id"), me.getId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(correctionRepository.findAll(spec, pageable(page, size)), this::toResponse);
    }

    private AttendanceCorrection loadPendingReviewable(Long id) {
        AttendanceCorrection c = correctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Correction request not found"));
        User reviewer = access.currentUser();
        Employee reviewerEmp = access.currentEmployee();
        if (!access.canReview(reviewer, reviewerEmp, c.getEmployee())) {
            throw new AccessDeniedException("You may not review this request");
        }
        if (c.getStatus() != CorrectionStatus.PENDING) {
            throw new ConflictException("This request has already been " + c.getStatus().name().toLowerCase());
        }
        return c;
    }

    private void finish(AttendanceCorrection c, CorrectionStatus status, String note) {
        c.setStatus(status);
        c.setReviewer(access.currentEmployee());
        c.setReviewedAt(clock.instant());
        c.setReviewNote(UserProvisioningService.blankToNull(note));
        correctionRepository.save(c);
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return time == null ? null : ZonedDateTime.of(date, time, clock.getZone()).toInstant();
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), AuditService.clamp(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private AttendanceDtos.CorrectionResponse toResponse(AttendanceCorrection c) {
        Employee e = c.getEmployee();
        return new AttendanceDtos.CorrectionResponse(c.getId(), e.getId(), e.getEmployeeCode(), e.getUser().getUsername(),
                c.getWorkDate(), c.getRequestedCheckIn(), c.getRequestedCheckOut(), c.getReason(), c.getStatus().name(),
                c.getReviewer() == null ? null : c.getReviewer().getUser().getUsername(),
                c.getReviewedAt(), c.getReviewNote(), c.getCreatedAt());
    }
}
