package com.workpulse.repository;

import com.workpulse.entity.AttendanceCorrection;
import com.workpulse.entity.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;

public interface AttendanceCorrectionRepository
        extends JpaRepository<AttendanceCorrection, Long>, JpaSpecificationExecutor<AttendanceCorrection> {

    boolean existsByEmployee_IdAndWorkDateAndStatus(Long employeeId, LocalDate workDate, CorrectionStatus status);
    long countByStatus(CorrectionStatus status);
}
