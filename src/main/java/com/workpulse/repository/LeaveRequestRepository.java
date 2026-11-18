package com.workpulse.repository;

import com.workpulse.entity.LeaveRequest;
import com.workpulse.entity.LeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(r) > 0 from LeaveRequest r where r.employee.id = :eid and r.status in :statuses "
            + "and r.startDate <= :end and r.endDate >= :start")
    boolean existsOverlap(@Param("eid") Long employeeId, @Param("start") LocalDate start,
                          @Param("end") LocalDate end, @Param("statuses") Collection<LeaveStatus> statuses);

    /** Rows of [leaveTypeId, status, totalDays] for one employee and one date window. */
    @Query("select r.leaveType.id, r.status, sum(r.dayCount) from LeaveRequest r "
            + "where r.employee.id = :eid and r.startDate between :from and :to and r.status in :statuses "
            + "group by r.leaveType.id, r.status")
    List<Object[]> sumDaysByTypeAndStatus(@Param("eid") Long employeeId, @Param("from") LocalDate from,
                                          @Param("to") LocalDate to, @Param("statuses") Collection<LeaveStatus> statuses);

    /** Approved leave of one employee overlapping a window. */
    @Query("select r from LeaveRequest r where r.employee.id = :eid and r.status = :status "
            + "and r.startDate <= :to and r.endDate >= :from")
    List<LeaveRequest> findOverlapping(@Param("eid") Long employeeId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to, @Param("status") LeaveStatus status);

    /** Employee ids that have approved leave covering the given date. */
    @Query("select distinct r.employee.id from LeaveRequest r where r.status = com.workpulse.entity.LeaveStatus.APPROVED "
            + "and r.startDate <= :d and r.endDate >= :d")
    List<Long> findEmployeeIdsOnLeave(@Param("d") LocalDate date);

    /** Approved leave of several employees overlapping one date (used by the roster). */
    @Query("select r from LeaveRequest r where r.status = com.workpulse.entity.LeaveStatus.APPROVED "
            + "and r.employee.id in :ids and r.startDate <= :d and r.endDate >= :d")
    List<LeaveRequest> findApprovedOnDate(@Param("ids") Collection<Long> employeeIds, @Param("d") LocalDate date);
}
