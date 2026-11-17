# Project Audit

Audit of the uploaded repository `WorkPulse_Module1_Hardened.zip` (66 files) performed before any change, followed by
the status of each finding after remediation. Statuses: **Fixed** (code changed; see verification notes), **Fixed – verified**
(executed in this environment), **Open** (documented limitation).

## What the repository actually contained

* **Backend:** only "Module 1": register, login, logout, `me`, admin-creates-user, two stub dashboard endpoints
  (`/api/admin/dashboard`, `/api/employee/dashboard`) and a `system-status` endpoint. One entity (`User`), one repository,
  one integration test class (15 tests). **No** employee, attendance, leave, department, notification, audit, dashboard or
  report backend existed.
* **Frontend:** a complete-looking multi-page UI, but every data page called endpoints that did not exist and silently fell
  back to an in-browser `MockDB`.
* **Build/config:** Spring Boot 3.3.4 / Java 17 / Maven, `ddl-auto=update`, no migrations, no CI, no README, no `.gitignore`.

## Findings

| ID | Sev | Finding | Affected files (original) | Impact | Status |
|---|---|---|---|---|---|
| A-01 | Critical | Frontend "features" were mock data: Clock-in, apply leave, approve/reject, notifications "read" state, employee list lived only in browser memory | `assets/js/api.js` (MockDB), `attendance.js`, `leaves.js`, `employees.js`, `notifications.js`, `layout.js` | Nothing persisted; users would believe actions were recorded | **Fixed** – MockDB deleted, every page rewritten against real endpoints; jsdom smoke test passes |
| A-02 | Critical | Dashboard showed hardcoded metrics (6 / 4 / 1 / 2 / 80 %) and a fake "MySQL 8 Connected" status | `dashboard.js`, `AdminController` | Fabricated production data | **Fixed** – `DashboardService` computes from DB; stub endpoints removed |
| A-03 | Critical | Missing backends for employees, attendance, leave, departments, notifications, audit, reports | `controller/*` | API calls 404 | **Fixed** (new modules) |
| A-04 | Critical | Hardcoded DB password default `Root@123` and seeded admin `admin@workpulse.com / Admin@123` (the latter violates the app's own 12-char policy) | `application.properties`, `DataInitializer` | Credential leak / default-admin takeover | **Fixed** – no defaults; env-driven bootstrap; weak bootstrap password aborts startup. *Existing databases keep the old admin: rotate it (README)* |
| A-05 | High | Demo credentials (including the admin password) rendered on the login page | `login.html`, `login.js` | Credential disclosure | **Fixed** |
| A-06 | High | `spring.jpa.hibernate.ddl-auto=update` in production config | `application.properties` | Unreviewed schema changes, no repeatable setup | **Fixed – verified** – Flyway V1–V4, `ddl-auto=none`; migrations executed on MySQL 8.0 and H2 (clean and upgrade-with-data) |
| A-07 | High | Generic 500 handler returned `ex.getMessage()` | `GlobalExceptionHandler` | Internal detail leakage | **Fixed** – generic message, cause logged server-side |
| A-08 | High | Deactivated/changed accounts kept working sessions | session handling | Revoked users retain access | **Fixed** – `SessionRevocationService` + filter (deactivation, role change, password change/reset) |
| A-09 | High | No security headers (CSP, frame, referrer) | `SecurityConfig` | Clickjacking/XSS hardening missing | **Fixed** – CSP `script-src 'self'`, `frame-ancestors 'none'`, etc.; inline scripts removed from pages |
| A-10 | Medium | Spring Security DEBUG logging enabled | `application.properties` | Sensitive data in logs | **Fixed** – per-profile logging |
| A-11 | Medium | Absolute `http://localhost:8080/api` base URL in JS | `config.js` | Breaks every non-local deployment, cross-origin cookies | **Fixed** – relative `/api` |
| A-12 | Medium | Google Fonts `@import` | `base.css` | Third-party request (privacy), conflicts with strict CSP | **Fixed** – system font stack |
| A-13 | Medium | Role model had no manager concept; employee-only/admin-only URL rules | `Role`, `SecurityConfig` | Cannot express approvals | **Fixed** – `MANAGER` role, `AccessService` review rules |
| A-14 | Medium | Login-throttle map grew without bound | `LoginRateLimiter` | Memory DoS | **Fixed** – stale entries pruned |
| A-15 | Medium | Authentication used a pre-existing session at login (fixation window) | `AuthService` | Session fixation | **Fixed** – old session invalidated, new one created (test included) |
| A-16 | Medium | Legacy static test console (`css/style.css`, `js/app.js`) and obsolete PowerShell/Postman scripts using now-invalid passwords | repo root, `static/` | Dead/misleading code | **Fixed** – removed/replaced |
| A-17 | Low | No README, `.gitignore`, `.env.example`, CI, Docker, API docs | repo | Cannot be published/run from a fresh checkout | **Fixed** |
| A-18 | Info | Existing databases created by Hibernate store `users.role` as MySQL `ENUM('ADMIN','EMPLOYEE')`; the new `MANAGER` role would be rejected ("Data truncated") | schema | Production break on upgrade (not visible with H2) | **Fixed – verified** on MySQL 8.0 (V2 widens the column) |
| A-19 | Info | Login throttling is in-memory per e-mail; sessions are in-memory | `LoginRateLimiter` | Account lock-out nuisance; single-instance only | **Open** – documented |
| A-20 | Info | Nothing in this environment could download Maven dependencies | environment | Build/test of the Spring application not executed | **Open** – see `PROGRESS.md` |

## Acceptance criteria for "Fixed"

Each fixed item is covered by at least one of: an integration test (`src/test/java/.../integration`), a unit test, a
migration executed on a real database, or the jsdom smoke/interaction scripts. See `FEATURE_GAP_ANALYSIS.md` for the
per-feature mapping and `PROGRESS.md` for what was executed vs. only written.
