package com.workpulse.repository;

import com.workpulse.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    boolean existsByHolidayDate(LocalDate date);
    boolean existsByHolidayDateAndIdNot(LocalDate date, Long id);
    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate from, LocalDate to);
}
