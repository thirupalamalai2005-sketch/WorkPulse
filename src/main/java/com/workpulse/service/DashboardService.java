package com.workpulse.service;

import com.workpulse.dto.AttendanceDtos;
import com.workpulse.dto.MiscDtos;
import com.workpulse.entity.*;
import com.workpulse.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** All figures come from persisted data; nothing is hardcoded. */
@Service
public class DashboardService {

    private final AccessService access;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository recordRepository;
    private final LeaveRequestRepository leaveRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final NotificationRepository notificationRepository;
    private final ScheduleService scheduleService;
    private final AttendanceService attendanceService;
    private final LeaveService leaveService;
    private final AuditService audit;
    private final Clock clock;

    public DashboardService(AccessService access, EmployeeRepository employeeRepository,
                            AttendanceRecordRepository recordRepository, LeaveRequestRepository leaveRepository,
                            AttendanceCorrectionRepository correctionRepository,
                            NotificationRepository notificationRepository, ScheduleService scheduleService,
                            AttendanceService attendanceService, LeaveService leaveService,
                            AuditService audit, Clock clock) {
        this.access = access;
        this.employeeRepository = employeeRepository;
        this.recordRepository = recordRepository;
        this.leaveRepository = leaveRepository;
        this.correctionRepository = correctionRepository;
        this.notificationRepository = notificationRepository;
        this.scheduleService = scheduleService;
        this.attendanceService = attendanceService;
        this.leaveService = leaveService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MiscDtos.Dashboard summary() {
        User viewer = access.currentUser();
        Employee me = access.currentEmployee();
        boolean admin = access.isAdmin(viewer);
        boolean manager = viewer.getRole() == Role.MANAGER;
        LocalDate today = LocalDate.now(clock);

        MiscDtos.Workforce workforce = (admin || manager) ? workforce(admin, me, today) : null;

        Optional<AttendanceRecord> rec = recordRepository.findByEmployee_IdAndWorkDate(me.getId(), today);
        String state = rec.isEmpty() ? "NOT_CHECKED_IN" : (rec.get().getCheckOutAt() == null ? "CHECKED_IN" : "CHECKED_OUT");
        LocalDate monthStart = today.withDayOfMonth(1);
        AttendanceDtos.Summary month = attendanceService.computeSummary(me.getId(), monthStart, today, today);
        long myPending = leaveRepository.count((root, q, cb) -> cb.and(
                cb.equal(root.get("employee").get("id"), me.getId()),
                cb.equal(root.get("status"), LeaveStatus.PENDING)));
        MiscDtos.Personal personal = new MiscDtos.Personal(state, leaveService.balancesFor(me.getId(), today.getYear()),
                myPending, notificationRepository.countByUserIdAndReadFlagFalse(viewer.getUserId()), month);

        return new MiscDtos.Dashboard(workforce, personal, admin ? audit.recent() : List.of());
    }

    private MiscDtos.Workforce workforce(boolean admin, Employee me, LocalDate today) {
        List<Employee> scope = admin ? employeeRepository.findByStatus(EmployeeStatus.ACTIVE)
                                     : employeeRepository.findByManager_IdAndStatus(me.getId(), EmployeeStatus.ACTIVE);
        Set<Long> ids = scope.stream().map(Employee::getId).collect(Collectors.toSet());
        boolean working = scheduleService.isWorkingDay(today, scheduleService.current());

        Set<Long> present = new HashSet<>(recordRepository.findEmployeeIdsByWorkDate(today));
        present.retainAll(ids);
        Set<Long> late = new HashSet<>(recordRepository.findLateEmployeeIdsByWorkDate(today));
        late.retainAll(ids);
        Set<Long> onLeave = new HashSet<>(leaveRepository.findEmployeeIdsOnLeave(today));
        onLeave.retainAll(ids);

        Set<Long> leaveOnly = new HashSet<>(onLeave);
        leaveOnly.removeAll(present);
        long absent = 0;
        if (working) {
            Set<Long> rest = new HashSet<>(ids);
            rest.removeAll(present);
            rest.removeAll(onLeave);
            absent = rest.size();
        }
        long expected = ids.size() - leaveOnly.size();
        int rate = (working && expected > 0) ? (int) Math.round(100.0 * present.size() / expected) : 0;

        Specification<LeaveRequest> pendingLeaves = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), LeaveStatus.PENDING));
            p.add(cb.notEqual(root.get("employee").get("id"), me.getId()));
            if (!admin) p.add(cb.equal(root.get("employee").get("manager").get("id"), me.getId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Specification<AttendanceCorrection> pendingCorrections = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), CorrectionStatus.PENDING));
            p.add(cb.notEqual(root.get("employee").get("id"), me.getId()));
            if (!admin) p.add(cb.equal(root.get("employee").get("manager").get("id"), me.getId()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return new MiscDtos.Workforce(admin ? "ORGANIZATION" : "TEAM", working, ids.size(), present.size(),
                late.size(), onLeave.size(), absent, leaveRepository.count(pendingLeaves),
                correctionRepository.count(pendingCorrections), rate);
    }
}
