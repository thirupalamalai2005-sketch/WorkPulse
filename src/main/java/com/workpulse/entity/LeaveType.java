package com.workpulse.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_types")
public class LeaveType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(name = "annual_entitlement", nullable = false)
    private int annualEntitlement;
    @Column(name = "allow_past_dates", nullable = false)
    private boolean allowPastDates;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAnnualEntitlement() { return annualEntitlement; }
    public void setAnnualEntitlement(int v) { this.annualEntitlement = v; }
    public boolean isAllowPastDates() { return allowPastDates; }
    public void setAllowPastDates(boolean v) { this.allowPastDates = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
