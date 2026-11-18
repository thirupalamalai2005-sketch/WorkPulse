package com.workpulse.repository;

import com.workpulse.entity.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> { }
