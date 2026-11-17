package com.workpulse.service;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.*;
import com.workpulse.exception.BadRequestException;
import com.workpulse.exception.ConflictException;
import com.workpulse.exception.ResourceNotFoundException;
import com.workpulse.repository.AttendanceRecordRepository;
import com.workpulse.repository.EmployeeRepository;
import com.workpulse.repository.LeaveRequestRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    static final int MAX_RANGE_DAYS = 366;

    private final AttendanceRecordRepository recordRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRepository;
    private final ScheduleService scheduleService;
    private final AccessService access;
    private final Clock clock;

    public AttendanceService(AttendanceRecordRepository recordRepository, EmployeeRepository employeeRepository,
                             LeaveRequestRepository leaveRepository, ScheduleService scheduleService,
                             AccessService access, Clock clock) {
        this.recordRepository = recordRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.scheduleService = scheduleService;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public AttendanceDtos.Entry checkIn() {
        Employee me = access.currentEmployee();
        LocalDate today = LocalDate.now(clock);
        if (recordRepository.findByEmployee_IdAndWorkDate(me.getId(), today).isPresent()) {
            throw new ConflictException("You have already checked in today");
        }
        WorkSchedule schedule = scheduleService.current();
        Instant now = clock.instant();
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(me);
        r.setWorkDate(today);
        r.setCheckInAt(now);
        r.setLate(scheduleService.isLate(now, today, schedule));
        r.setSource(AttendanceRecord.SOURCE_CHECK_IN);
        // flush now so a concurrent duplicate hits the unique constraint inside this method (-> 409)
        return toEntry(recordRepository.saveAndFlush(r), today);
    }

    @Transactional
    public AttendanceDtos.Entry checkOut() {
        Employee me = access.currentEmployee();
        LocalDate today = LocalDate.now(clock);
        AttendanceRecord r = recordRepository.findByEmployee_IdAndWorkDate(me.getId(), today)
                .orElseThrow(() -> new ConflictException(
                        "You have not checked in today. If you forgot to check out earlier, submit a correction request."));
        if (r.getCheckOutAt() != null) {
            throw new ConflictException("You have already checked out today");
        }
        r.setCheckOutAt(clock.instant());
        return toEntry(recordRepository.save(r), today);
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.TodayStatus today() {
        Employee me = access.currentEmployee();
        LocalDate today = LocalDate.now(clock);
        Optional<AttendanceRecord> rec = recordRepository.findByEmployee_IdAndWorkDate(me.getId(), today);
        String state = rec.isEmpty() ? "NOT_CHECKED_IN" : (rec.get().getCheckOutAt() == null ? "CHECKED_IN" : "CHECKED_OUT");
        boolean working = scheduleService.isWorkingDay(today, scheduleService.current());
        boolean onLeave = !leaveRepository.findOverlapping(me.getId(), today, today, LeaveStatus.APPROVED).isEmpty();
        return new AttendanceDtos.TodayStatus(today, state, working,
                working ? null : "Non-working day (weekly off or holiday)", onLeave,
                rec.map(r -> toEntry(r, today)).orElse(null), clock.getZone().getId());
    }

    /** History of one employee: yourself by default; admin/manager (of that employee) may pass employeeId. */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Entry> history(Long employeeId, LocalDate from, LocalDate to, int page, int size) {
        Employee target = resolveTarget(employeeId);
        LocalDate today = LocalDate.now(clock);
        LocalDate[] range = normalizeRange(from, to, today);
        var result = recordRepository.findByEmployee_IdAndWorkDateBetween(target.getId(), range[0], range[1],
                PageRequest.of(Math.max(page, 0), AuditService.clamp(size), Sort.by(Sort.Direction.DESC, "workDate")));
        return PageResponse.of(result, r -> toEntry(r, today));
    }

    /** Summary computed from stored records, the work schedule, holidays and approved leave. */
    @Transactional(readOnly = true)
    public AttendanceDtos.Summary summary(Long employeeId, LocalDate from, LocalDate to) {
        Employee target = resolveTarget(employeeId);
        LocalDate today = LocalDate.now(clock);
        LocalDate[] range = normalizeRange(from, to, today);
        return computeSummary(target.getId(), range[0], range[1], today);
    }

    AttendanceDtos.Summary computeSummary(Long employeeId, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate effectiveTo = to.isAfter(today) ? today : to; // the future has no attendance yet
        List<AttendanceRecord> records = recordRepository.findByEmployee_IdAndWorkDateBetween(employeeId, from, to);
        Set<LocalDate> presentDates = records.stream().map(AttendanceRecord::getWorkDate).collect(Collectors.toSet());
        List<LocalDate> workingDays = scheduleService.workingDaysBetween(from, effectiveTo);

        Set<LocalDate> leaveDates = new HashSet<>();
        for (LeaveRequest lr : leaveRepository.findOverlapping(employeeId, from, effectiveTo, LeaveStatus.APPROVED)) {
            for (LocalDate d = lr.getStartDate(); !d.isAfter(lr.getEndDate()); d = d.plusDays(1)) leaveDates.add(d);
        }
        int leave = 0, absent = 0;
        for (LocalDate d : workingDays) {
            if (presentDates.contains(d)) continue;
            if (d.equals(today)) continue; // today is still in progress: not absent (yet)
            if (leaveDates.contains(d)) leave++; else absent++;
        }
        int late = (int) records.stream().filter(AttendanceRecord::isLate).count();
        int missed = (int) records.stream().filter(r -> r.getCheckOutAt() == null && r.getWorkDate().isBefore(today)).count();
        long minutes = records.stream().filter(r -> r.getCheckOutAt() != null)
                .mapToLong(r -> Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes()).sum();
        return new AttendanceDtos.Summary(from, to, workingDays.size(), records.size(), late, missed, leave, absent, minutes);
    }

    /** One row per active employee in the caller's scope (admin: everyone, manager: direct reports). */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.RosterEntry> roster(LocalDate date, int page, int size) {
        User viewer = access.currentUser();
        Employee viewerEmp = access.currentEmployee();
        LocalDate day = date != null ? date : LocalDate.now(clock);
        boolean admin = access.isAdmin(viewer);

        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), EmployeeStatus.ACTIVE));
            if (!admin) p.add(cb.equal(root.get("manager").get("id"), viewerEmp.getId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var employees = employeeRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), AuditService.clamp(size), Sort.by("user.username")));

        List<Long> ids = employees.getContent().stream().map(Employee::getId).toList();
        Map<Long, AttendanceRecord> records = ids.isEmpty() ? Map.of()
                : recordRepository.findByWorkDateAndEmployee_IdIn(day, ids).stream()
                    .collect(Collectors.toMap(r -> r.getEmployee().getId(), Function.identity()));
        Set<Long> onLeave = ids.isEmpty() ? Set.of()
                : leaveRepository.findApprovedOnDate(ids, day).stream()
                    .map(l -> l.getEmployee().getId()).collect(Collectors.toSet());
        WorkSchedule schedule = scheduleService.current();
        boolean weekday = schedule.workingDaySet().contains(day.getDayOfWeek());
        boolean working = scheduleService.isWorkingDay(day, schedule);
        LocalDate today = LocalDate.now(clock);

        return PageResponse.of(employees, e -> {
            AttendanceRecord r = records.get(e.getId());
            String status;
            if (r != null) {
                status = (r.getCheckOutAt() == null && day.isBefore(today)) ? "MISSED_CHECKOUT" : (r.isLate() ? "LATE" : "PRESENT");
            } else if (onLeave.contains(e.getId())) status = "ON_LEAVE";
            else if (!working) status = weekday ? "HOLIDAY" : "WEEKLY_OFF";
            else if (day.isAfter(today)) status = "UPCOMING";
            else if (day.equals(today)) status = "NOT_CHECKED_IN";
            else status = "ABSENT";
            return new AttendanceDtos.RosterEntry(e.getId(), e.getEmployeeCode(), e.getUser().getUsername(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(), status,
                    r == null ? null : r.getCheckInAt(), r == null ? null : r.getCheckOutAt());
        });
    }

    private Employee resolveTarget(Long employeeId) {
        Employee me = access.currentEmployee();
        if (employeeId == null || employeeId.equals(me.getId())) return me;
        Employee target = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        access.requireCanViewPrivate(target); // admin or direct manager only; others get 403
        return target;
    }

    static LocalDate[] normalizeRange(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) throw new BadRequestException("'from' date must not be after 'to' date");
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDate[]{start, end};
    }

    static AttendanceDtos.Entry toEntry(AttendanceRecord r, LocalDate today) {
        Employee e = r.getEmployee();
        boolean missed = r.getCheckOutAt() == null && r.getWorkDate().isBefore(today);
        Long minutes = r.getCheckOutAt() == null ? null
                : Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
        return new AttendanceDtos.Entry(r.getId(), e.getId(), e.getEmployeeCode(), e.getUser().getUsername(),
                r.getWorkDate(), r.getCheckInAt(), r.getCheckOutAt(), r.isLate(), missed, minutes, r.getSource());
    }
}
