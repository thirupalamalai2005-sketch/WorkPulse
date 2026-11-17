package com.workpulse.service;

import com.workpulse.dto.OrgDtos;
import com.workpulse.entity.WorkSchedule;
import com.workpulse.exception.BadRequestException;
import com.workpulse.repository.HolidayRepository;
import com.workpulse.repository.WorkScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Work schedule, holidays and the "is this a working day" rules used by attendance and leave. */
@Service
public class ScheduleService {

    private final WorkScheduleRepository scheduleRepository;
    private final HolidayRepository holidayRepository;
    private final AuditService audit;
    private final Clock clock;

    public ScheduleService(WorkScheduleRepository scheduleRepository, HolidayRepository holidayRepository,
                           AuditService audit, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.holidayRepository = holidayRepository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkSchedule current() {
        return scheduleRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Work schedule row is missing (migration V2 not applied?)"));
    }

    @Transactional(readOnly = true)
    public OrgDtos.ScheduleResponse getResponse() {
        return toResponse(current());
    }

    @Transactional
    public OrgDtos.ScheduleResponse update(OrgDtos.ScheduleRequest req) {
        if (!req.startTime().isBefore(req.endTime())) {
            throw new BadRequestException("Start time must be before end time");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String d : req.workingDays()) {
            DayOfWeek match = null;
            for (DayOfWeek candidate : DayOfWeek.values()) {
                if (candidate.name().startsWith(d.trim().toUpperCase()) && d.trim().length() >= 3) match = candidate;
            }
            if (match == null) throw new BadRequestException("Unknown working day: " + d);
            days.add(match);
        }
        WorkSchedule s = current();
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setGraceMinutes(req.graceMinutes());
        s.setWorkingDays(days.stream().sorted().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(",")));
        scheduleRepository.save(s);
        audit.logCurrent("SCHEDULE_UPDATED", "SCHEDULE", s.getId(),
                "start=" + s.getStartTime() + ", end=" + s.getEndTime() + ", days=" + s.getWorkingDays());
        return toResponse(s);
    }

    public boolean isWorkingDay(LocalDate date, WorkSchedule schedule) {
        return schedule.workingDaySet().contains(date.getDayOfWeek()) && !holidayRepository.existsByHolidayDate(date);
    }

    /** Working days in [from, to] inclusive, skipping non-working weekdays and holidays. One DB query. */
    @Transactional(readOnly = true)
    public List<LocalDate> workingDaysBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) return List.of();
        WorkSchedule schedule = current();
        Set<DayOfWeek> weekdays = schedule.workingDaySet();
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to).stream()
                .map(h -> h.getHolidayDate()).collect(Collectors.toSet());
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (weekdays.contains(d.getDayOfWeek()) && !holidays.contains(d)) result.add(d);
        }
        return result;
    }

    /** Late means: checked in after start time + grace, on a working day. */
    public boolean isLate(Instant checkIn, LocalDate workDate, WorkSchedule schedule) {
        if (!isWorkingDay(workDate, schedule)) return false;
        LocalTime local = checkIn.atZone(clock.getZone()).toLocalTime();
        return local.isAfter(schedule.getStartTime().plusMinutes(schedule.getGraceMinutes()));
    }

    private OrgDtos.ScheduleResponse toResponse(WorkSchedule s) {
        List<String> days = s.workingDaySet().stream().sorted().map(d -> d.name().substring(0, 3)).toList();
        return new OrgDtos.ScheduleResponse(s.getStartTime(), s.getEndTime(), s.getGraceMinutes(), days,
                clock.getZone().getId(), clock.instant());
    }
}
