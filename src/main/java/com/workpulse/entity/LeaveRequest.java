package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    /** Number of working days (weekends and holidays excluded). */
    @Column(name = "day_count", nullable = false)
    private int dayCount;
    @Column(nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Employee reviewer;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", length = 500)
    private String reviewNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LeaveType getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveType leaveType) { this.leaveType = leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getDayCount() { return dayCount; }
    public void setDayCount(int dayCount) { this.dayCount = dayCount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public Employee getReviewer() { return reviewer; }
    public void setReviewer(Employee reviewer) { this.reviewer = reviewer; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
}
