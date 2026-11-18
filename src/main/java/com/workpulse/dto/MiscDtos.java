package com.workpulse.dto;

import java.time.Instant;
import java.util.List;

public final class MiscDtos {
    private MiscDtos() { }

    public record NotificationResponse(Long id, String type, String title, String message, String link,
                                       boolean read, Instant createdAt) { }

    public record AuditLogResponse(Long id, String actorEmail, String action, String resourceType,
                                   String resourceId, String details, String ipAddress, Instant createdAt) { }

    public record UnreadCount(long unread) { }

    /** Organisation/team figures. Null for plain employees. */
    public record Workforce(String scope, boolean workingDay, long totalEmployees, long presentToday,
                            long lateToday, long onLeaveToday, long absentToday, long pendingLeaves,
                            long pendingCorrections, int attendanceRate) { }

    public record Personal(String todayState, List<LeaveDtos.Balance> leaveBalances, long myPendingLeaves,
                           long unreadNotifications, AttendanceDtos.Summary monthSummary) { }

    public record Dashboard(Workforce workforce, Personal personal, List<AuditLogResponse> recentActivity) { }
}
