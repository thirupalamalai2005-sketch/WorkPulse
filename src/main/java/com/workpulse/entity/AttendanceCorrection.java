package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "requested_check_in")
    private Instant requestedCheckIn;
    @Column(name = "requested_check_out")
    private Instant requestedCheckOut;
    @Column(nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status = CorrectionStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Employee reviewer;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", length = 500)
    private String reviewNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Instant getRequestedCheckIn() { return requestedCheckIn; }
    public void setRequestedCheckIn(Instant v) { this.requestedCheckIn = v; }
    public Instant getRequestedCheckOut() { return requestedCheckOut; }
    public void setRequestedCheckOut(Instant v) { this.requestedCheckOut = v; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public CorrectionStatus getStatus() { return status; }
    public void setStatus(CorrectionStatus status) { this.status = status; }
    public Employee getReviewer() { return reviewer; }
    public void setReviewer(Employee reviewer) { this.reviewer = reviewer; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Instant getCreatedAt() { return createdAt; }
}
