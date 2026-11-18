# WorkPulse API

Base path `/api`. JSON in/out. Authentication is a server-side session cookie (`JSESSIONID`) created by
`POST /api/auth/login`. **All state-changing requests** (POST/PUT/PATCH/DELETE, except login and register) must carry the
CSRF token: copy the `XSRF-TOKEN` cookie value into the `X-XSRF-TOKEN` header.

Dates are ISO (`2026-03-04`), timestamps are UTC ISO-8601 instants (`2026-03-04T03:30:00Z`), times of day are `HH:mm`.
Lists that can grow are paged: `?page=0&size=10` → `{ content[], page, size, totalElements, totalPages }` (size capped at 100).

**Roles:** `public` · `auth` (any signed-in user) · `reviewer` (ADMIN or MANAGER) · `admin`.
"Scoped" means the service additionally limits rows (e.g. a manager only sees direct reports).

## Errors

```json
{ "timestamp": "…", "status": 400, "error": "Bad Request", "message": "…", "path": "/api/leaves",
  "validationErrors": { "reason": "Reason is required" } }
```

| Status | Meaning |
|---|---|
| 400 | validation failure or business-rule violation (insufficient balance, bad date range, …) |
| 401 | not signed in / session expired / bad credentials (always the same message for bad e-mail or password) |
| 403 | role not allowed, resource belongs to someone else, or missing CSRF token |
| 404 | resource does not exist (or is not visible to you) |
| 409 | state conflict (duplicate check-in, overlapping leave, already decided, duplicate name/e-mail, safeguards) |
| 429 | login throttled (`Retry-After` header) |
| 500 | unexpected error (generic message; details only in server logs) |

## Auth

| Method & path | Who | Notes |
|---|---|---|
| `POST /auth/register` | public, only if `REGISTRATION_ENABLED=true` | `{username,email,password}` → 201. Always `EMPLOYEE`; any `role` in the body is ignored |
| `POST /auth/login` | public | `{email,password}` → 200 + session cookie; 401 generic message; 429 after 5 failures |
| `POST /auth/logout` | auth | invalidates the session |
| `GET /auth/me` | auth | current user incl. `employeeId`, `employeeCode` |
| `GET /auth/config` | public | `{registrationEnabled}` |

## Account

| | Who | Notes |
|---|---|---|
| `GET /account/profile` | auth | own employee profile |
| `PUT /account/profile` | auth | `{username,phone}` only (e-mail/role/status are not editable here) |
| `POST /account/password` | auth | `{currentPassword,newPassword}`; verifies current password; ends **all** sessions of the user |
| `POST /admin/users` | admin | create account + profile: `{username,email,password,role,departmentId?,managerId?,designation?,phone?,joinDate?}` → 201 |
| `POST /admin/users/{userId}/reset-password` | admin | `{newPassword}`; ends the user's sessions |

## Employees

| | Who | Notes |
|---|---|---|
| `GET /employees?q&departmentId&status&sort&dir&page&size` | auth | `sort` ∈ name, email, code, joinDate, status, department. Non-admins see only ACTIVE employees; `phone`, `role`, `userId` are returned only for self / direct manager / admin |
| `GET /employees/{id}` | auth | same field rules |
| `PUT /employees/{id}` | admin | `{username,departmentId,managerId,designation,phone,joinDate}`; manager must be an active MANAGER/ADMIN and must not create a loop |
| `PATCH /employees/{id}/status` | admin | `{active}`; cannot deactivate yourself, the last admin, or someone with direct reports; ends sessions |
| `PATCH /employees/{id}/role` | admin | `{role}`; cannot change your own role / demote the last admin / strip a manager with reports; ends sessions |

## Departments & calendar

| | Who | Notes |
|---|---|---|
| `GET /departments` | auth | with `employeeCount` |
| `POST /departments`, `PUT /departments/{id}` | admin | `{name,description}`; unique name (case-insensitive) → 409 |
| `DELETE /departments/{id}?reassignTo={id}` | admin | 409 if employees remain and `reassignTo` is missing; otherwise moves them first |
| `GET /holidays?year` · `POST /holidays` · `DELETE /holidays/{id}` | auth · admin · admin | `{date,name}`; one holiday per date |
| `GET /schedule` · `PUT /schedule` | auth · admin | `{startTime,endTime,graceMinutes,workingDays[]}`; response includes `timezone` |

## Attendance

| | Who | Notes |
|---|---|---|
| `POST /attendance/check-in` | auth | 201; 409 if already checked in today |
| `POST /attendance/check-out` | auth | 409 if not checked in or already checked out |
| `GET /attendance/today` | auth | `state` NOT_CHECKED_IN / CHECKED_IN / CHECKED_OUT, working-day/leave flags |
| `GET /attendance?employeeId&from&to&page&size` | auth | own history; `employeeId` only for admin or the employee's manager (else 403); range ≤ 366 days |
| `GET /attendance/summary?employeeId&from&to` | auth | working/present/late/absent/leave days, missed check-outs, minutes worked |
| `GET /attendance/roster?date&page&size` | reviewer, scoped | status per active employee: PRESENT, LATE, ON_LEAVE, ABSENT, HOLIDAY, WEEKLY_OFF, MISSED_CHECKOUT, NOT_CHECKED_IN, UPCOMING |
| `POST /attendance/corrections` | auth | `{workDate,checkInTime?,checkOutTime?,reason}`; last 30 days, one pending per day |
| `GET /attendance/corrections/mine` | auth | own requests |
| `GET /attendance/corrections/review?status` | reviewer, scoped | others' requests (admin: all; manager: direct reports) |
| `POST /attendance/corrections/{id}/approve` · `/reject` | reviewer, scoped | `{note}` (required for reject); never your own; 409 once decided |

## Leave

| | Who | Notes |
|---|---|---|
| `GET /leaves/types?includeInactive` · `PUT /leaves/types/{id}` | auth · admin | `{name,annualEntitlement,allowPastDates,active}` |
| `GET /leaves/balances?employeeId&year` | auth | `employeeId` only for admin / manager of that employee |
| `POST /leaves` | auth | `{leaveTypeId,startDate,endDate,reason}` → 201 (PENDING). 400: bad dates, no working days, spans two years, past date not allowed, insufficient balance; 409: overlap |
| `GET /leaves?status` | auth | own requests |
| `GET /leaves/review?status` | reviewer, scoped | requests the caller may decide |
| `GET /leaves/{id}` | owner or permitted reviewer | otherwise 403 |
| `POST /leaves/{id}/approve` · `/reject` | reviewer, scoped | `{note}` (required for reject); not your own; 409 if already decided |
| `POST /leaves/{id}/cancel` | owner | pending, or approved and not yet started |

## Notifications

| | Who | Notes |
|---|---|---|
| `GET /notifications?unreadOnly&page&size` | auth | only the caller's |
| `GET /notifications/unread-count` | auth | `{unread}` |
| `POST /notifications/{id}/read` | auth | 404 if it is not yours |
| `POST /notifications/read-all` | auth | |

## Dashboard, reports, audit

| | Who | Notes |
|---|---|---|
| `GET /dashboard/summary` | auth | `workforce` (admin: ORGANIZATION, manager: TEAM, employee: null), `personal`, `recentActivity` (admin only) |
| `GET /reports/attendance?from&to&departmentId&q&page&size` · `.csv` | reviewer, scoped | |
| `GET /reports/leaves?status&from&to&departmentId&leaveTypeId&q&page&size` · `.csv` | reviewer, scoped | CSV ≤ 50 000 rows; cells starting with `= + - @` are prefixed with `'` |
| `GET /audit-logs?action&actor&resourceType&from&to&page&size` | admin | read-only; no write endpoints exist |

## Health

`GET /actuator/health` — public, `{"status":"UP"}`.
