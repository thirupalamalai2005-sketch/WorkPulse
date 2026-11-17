package com.workpulse.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit record: there are deliberately no setters, update or delete operations. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_email", length = 150, updatable = false)
    private String actorEmail;
    @Column(nullable = false, length = 50, updatable = false)
    private String action;
    @Column(name = "resource_type", nullable = false, length = 50, updatable = false)
    private String resourceType;
    @Column(name = "resource_id", length = 50, updatable = false)
    private String resourceId;
    @Column(length = 1000, updatable = false)
    private String details;
    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() { }

    public AuditLog(String actorEmail, String action, String resourceType, String resourceId,
                    String details, String ipAddress) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
