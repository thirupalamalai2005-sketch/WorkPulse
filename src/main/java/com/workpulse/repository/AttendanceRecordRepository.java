package com.workpulse.repository;

import com.workpulse.entity.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, Long>, JpaSpecificationExecutor<AttendanceRecord> {

    Optional<AttendanceRecord> findByEmployee_IdAndWorkDate(Long employeeId, LocalDate workDate);

    Page<AttendanceRecord> findByEmployee_IdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to, Pageable pageable);

    List<AttendanceRecord> findByEmployee_IdAndWorkDateBetween(Long employeeId, LocalDate from, LocalDate to);

    List<AttendanceRecord> findByWorkDateAndEmployee_IdIn(LocalDate workDate, Collection<Long> employeeIds);

    @Query("select a.employee.id from AttendanceRecord a where a.workDate = :d")
    List<Long> findEmployeeIdsByWorkDate(@Param("d") LocalDate date);

    @Query("select a.employee.id from AttendanceRecord a where a.workDate = :d and a.late = true")
    List<Long> findLateEmployeeIdsByWorkDate(@Param("d") LocalDate date);
}
