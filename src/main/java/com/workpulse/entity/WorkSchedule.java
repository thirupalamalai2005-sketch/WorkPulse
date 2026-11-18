package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/** Single company-wide schedule (row id = 1). */
@Entity
@Table(name = "work_schedules")
public class WorkSchedule {
    @Id
    private Long id;
    @Column(nullable = false, length = 50)
    private String name;
    @Column(name = "start_time", nullable = false)
    @Convert(converter = LocalTimeConverter.class)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    @Convert(converter = LocalTimeConverter.class)
    private LocalTime endTime;
    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes;
    /** Comma separated three-letter day codes, e.g. MON,TUE,WED */
    @Column(name = "working_days", nullable = false, length = 40)
    private String workingDays;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Set<DayOfWeek> workingDaySet() {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (String code : workingDays.split(",")) {
            for (DayOfWeek d : DayOfWeek.values()) {
                if (d.name().startsWith(code.trim().toUpperCase())) set.add(d);
            }
        }
        return set;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }
    public String getWorkingDays() { return workingDays; }
    public void setWorkingDays(String workingDays) { this.workingDays = workingDays; }
}
