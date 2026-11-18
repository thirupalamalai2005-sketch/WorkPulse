package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    public static final String SOURCE_CHECK_IN = "CHECK_IN";
    public static final String SOURCE_CORRECTION = "CORRECTION";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;
    @Column(name = "check_out_at")
    private Instant checkOutAt;
    @Column(nullable = false)
    private boolean late;
    @Column(nullable = false, length = 20)
    private String source = SOURCE_CHECK_IN;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Instant getCheckInAt() { return checkInAt; }
    public void setCheckInAt(Instant checkInAt) { this.checkInAt = checkInAt; }
    public Instant getCheckOutAt() { return checkOutAt; }
    public void setCheckOutAt(Instant checkOutAt) { this.checkOutAt = checkOutAt; }
    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
