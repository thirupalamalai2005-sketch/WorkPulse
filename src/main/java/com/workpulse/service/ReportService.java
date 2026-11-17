package com.workpulse.service;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.LeaveDtos;
import com.workpulse.dto.PageResponse;
import com.workpulse.entity.*;
import com.workpulse.repository.AttendanceRecordRepository;
import com.workpulse.repository.LeaveRequestRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filterable attendance/leave reports for admins (everyone) and managers (direct reports), with CSV export. */
@Service
public class ReportService {

    static final int MAX_CSV_ROWS = 50_000;

    private final AttendanceRecordRepository recordRepository;
    private final LeaveRequestRepository leaveRepository;
    private final AccessService access;
    private final LeaveService leaveService;
    private final AuditService audit;
    private final Clock clock;

    public ReportService(AttendanceRecordRepository recordRepository, LeaveRequestRepository leaveRepository,
                         AccessService access, LeaveService leaveService, AuditService audit, Clock clock) {
        this.recordRepository = recordRepository;
        this.leaveRepository = leaveRepository;
        this.access = access;
        this.leaveService = leaveService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Entry> attendance(LocalDate from, LocalDate to, Long departmentId, String q,
                                                         int page, int size) {
        LocalDate today = LocalDate.now(clock);
        LocalDate[] r = AttendanceService.normalizeRange(from, to, today);
        var result = recordRepository.findAll(attendanceSpec(r[0], r[1], departmentId, q),
                PageRequest.of(Math.max(page, 0), AuditService.clamp(size), attendanceSort()));
        return PageResponse.of(result, rec -> AttendanceService.toEntry(rec, today));
    }

    @Transactional
    public String attendanceCsv(LocalDate from, LocalDate to, Long departmentId, String q) {
        LocalDate today = LocalDate.now(clock);
        LocalDate[] r = AttendanceService.normalizeRange(from, to, today);
        List<AttendanceRecord> rows = recordRepository.findAll(attendanceSpec(r[0], r[1], departmentId, q),
                PageRequest.of(0, MAX_CSV_ROWS, attendanceSort())).getContent();
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(CsvWriter.row("Employee Code", "Employee", "Date", "Check In", "Check Out", "Late", "Missed Check-out",
                "Worked Minutes", "Source"));
        for (AttendanceRecord rec : rows) {
            AttendanceDtos.Entry e = AttendanceService.toEntry(rec, today);
            sb.append(CsvWriter.row(e.employeeCode(), e.employeeName(), e.workDate(), e.checkInAt(), e.checkOutAt(),
                    e.late() ? "Yes" : "No", e.missedCheckout() ? "Yes" : "No", e.workedMinutes(), e.source()));
        }
        audit.logCurrent("REPORT_EXPORTED", "REPORT", "attendance", "from=" + r[0] + ", to=" + r[1] + ", rows=" + rows.size());
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveDtos.Response> leaves(LeaveStatus status, LocalDate from, LocalDate to, Long departmentId,
                                                   Long leaveTypeId, String q, int page, int size) {
        Long viewerEmpId = access.currentEmployee().getId();
        var result = leaveRepository.findAll(leaveSpec(status, from, to, departmentId, leaveTypeId, q),
                PageRequest.of(Math.max(page, 0), AuditService.clamp(size), leaveSort()));
        return PageResponse.of(result, l -> leaveService.toResponse(l, viewerEmpId));
    }

    @Transactional
    public String leavesCsv(LeaveStatus status, LocalDate from, LocalDate to, Long departmentId, Long leaveTypeId, String q) {
        Long viewerEmpId = access.currentEmployee().getId();
        List<LeaveRequest> rows = leaveRepository.findAll(leaveSpec(status, from, to, departmentId, leaveTypeId, q),
                PageRequest.of(0, MAX_CSV_ROWS, leaveSort())).getContent();
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(CsvWriter.row("Employee Code", "Employee", "Leave Type", "Start", "End", "Working Days", "Status",
                "Reviewer", "Reviewed At", "Reason"));
        for (LeaveRequest l : rows) {
            LeaveDtos.Response e = leaveService.toResponse(l, viewerEmpId);
            sb.append(CsvWriter.row(e.employeeCode(), e.employeeName(), e.leaveTypeName(), e.startDate(), e.endDate(),
                    e.days(), e.status(), e.reviewerName(), e.reviewedAt(), e.reason()));
        }
        audit.logCurrent("REPORT_EXPORTED", "REPORT", "leaves", "rows=" + rows.size());
        return sb.toString();
    }

    // ------------------------------------------------------------------ specifications

    private Specification<AttendanceRecord> attendanceSpec(LocalDate from, LocalDate to, Long departmentId, String q) {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<AttendanceRecord, Employee> emp = root.join("employee");
            p.add(cb.between(root.get("workDate"), from, to));
            addEmployeeFilters(p, cb, emp, departmentId, q, admin, me);
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private Specification<LeaveRequest> leaveSpec(LeaveStatus status, LocalDate from, LocalDate to, Long departmentId,
                                                  Long leaveTypeId, String q) {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<LeaveRequest, Employee> emp = root.join("employee");
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
            if (leaveTypeId != null) p.add(cb.equal(root.get("leaveType").get("id"), leaveTypeId));
            addEmployeeFilters(p, cb, emp, departmentId, q, admin, me);
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private void addEmployeeFilters(List<Predicate> p, jakarta.persistence.criteria.CriteriaBuilder cb,
                                    Join<?, Employee> emp, Long departmentId, String q, boolean admin, Employee me) {
        if (!admin) p.add(cb.equal(emp.get("manager").get("id"), me.getId())); // managers: direct reports only
        if (departmentId != null) p.add(cb.equal(emp.get("department").get("id"), departmentId));
        if (q != null && !q.isBlank()) {
            String like = "%" + AuditService.escapeLike(q.trim().toLowerCase()) + "%";
            p.add(cb.or(cb.like(cb.lower(emp.get("user").get("username")), like, '\\'),
                        cb.like(cb.lower(emp.get("employeeCode")), like, '\\')));
        }
    }

    private Sort attendanceSort() {
        return Sort.by(Sort.Direction.DESC, "workDate").and(Sort.by(Sort.Direction.ASC, "employee.employeeCode"));
    }

    private Sort leaveSort() {
        return Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
