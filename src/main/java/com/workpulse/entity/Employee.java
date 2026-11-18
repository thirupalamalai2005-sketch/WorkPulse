package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/** HR profile of a user. Every user has exactly one employee record. */
@Entity
@Table(name = "employees")
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;
    @Column(length = 100)
    private String designation;
    @Column(length = 20)
    private String phone;
    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public static String codeFor(Long userId) { return String.format("WP-%04d", userId); }

    public Long getId() { return id; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Employee getManager() { return manager; }
    public void setManager(Employee manager) { this.manager = manager; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getJoinDate() { return joinDate; }
    public void setJoinDate(LocalDate joinDate) { this.joinDate = joinDate; }
    public EmployeeStatus getStatus() { return status; }
    public void setStatus(EmployeeStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
