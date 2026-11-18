# Feature Gap Analysis

**Before** = state of the uploaded repository (verified by reading the code, not by trusting page names).
**After** = state now. Classification: *Fully implemented and verified*, *Partially implemented*, *Missing*, *Broken*,
*Needs verification*.

**Verification key** — what "verified" means in this document:

* **EXEC** – executed here and passed (SQL migrations on MySQL 8.0.46 and H2 2.2; 6 pure unit tests with JUnit 5; 12 pages and
  the modal-form flow loaded/exercised in jsdom with a fake API; `node --check`; `javac` parse of all Java sources).
* **WRITTEN** – implemented and covered by Spring integration tests that were **written but not executed**, because Maven
  Central is unreachable from the development sandbox (so the application could not be compiled against Spring or started).
  Treat these as "needs verification" until `./mvnw verify` (or the CI workflow) is green.

Hence no feature below is claimed as "fully verified end to end" unless it carries EXEC for the part that matters.

## Matrix

| # | Feature | Before | After | Verification | Main files |
|---|---|---|---|---|---|
| A1 | Registration | Partially implemented (backend ok, no UI) | Implemented; off by default via `REGISTRATION_ENABLED`; UI page added; always `EMPLOYEE` | WRITTEN (`AuthIntegrationTest`), UI EXEC | `AuthService`, `register.html` |
| A2 | Login / logout / session invalidation | Fully implemented (Module 1 tests), but sessions survived deactivation; fixation window | Hardened: session rotation, revocation on deactivate/role/password change | WRITTEN | `AuthService`, `SessionRevocation*` |
| A3 | BCrypt, password policy | Fully implemented | Unchanged; policy extracted to reusable static rule | **EXEC** (`PasswordPolicyTest`) | `PasswordConstraintValidator` |
| A4 | Profile view/edit | Missing (page was mock) | Implemented (name, phone) | WRITTEN, UI EXEC | `AccountController/Service`, `profile.js` |
| A5 | Password change with current-password check | Missing | Implemented; ends all sessions | WRITTEN | `AccountService` |
| A6 | Password reset workflow | Missing | **Admin-initiated reset only**; self-service e-mail reset deliberately not built (no reliable mail config) | WRITTEN | `AdminUserController` |
| A7 | Admin account creation + role assignment | Partially (create only) | Create with profile/department/manager; change role | WRITTEN | `EmployeeService` |
| A8 | Activate / deactivate accounts | Missing | Implemented with last-admin / self / reports safeguards | WRITTEN | `EmployeeService.setActive` |
| A9 | Duplicate e-mail prevention | Fully implemented | Unchanged (DB unique + service check, case-insensitive) | WRITTEN | `UserProvisioningService` |
| A10 | Login throttling, safe errors | Fully implemented | Same + bounded memory; generic message incl. disabled accounts | WRITTEN | `LoginRateLimiter` |
| A11 | Server-side role + ownership checks | Partially (two role rules) | `@PreAuthorize` on every endpoint + `AccessService` ownership/scope rules | WRITTEN | all controllers |
| B1 | Employee CRUD, deactivation | Missing (mock) | Create/list/view/edit/deactivate; profile & department info | WRITTEN, UI EXEC | `EmployeeService` |
| B2 | Search, filter, sort, pagination | Missing | Implemented with sort whitelist and escaped LIKE | WRITTEN | `EmployeeService.list` |
| B3 | Manager assignment | Missing | Implemented (role-validated, loop-safe) | WRITTEN | `EmployeeService.resolveManager` |
| B4 | Restricted personal info | Missing | phone/role/userId only for self, manager, admin | WRITTEN | `EmployeeMapper` |
| C1 | Check-in / out, status | Broken (mock only) | Implemented; unique (employee, day) constraint; clock-injected | WRITTEN; constraint EXEC (migration) | `AttendanceService` |
| C2 | History + date ranges, daily/weekly/monthly summary | Broken | Implemented from stored records | WRITTEN, UI EXEC | `AttendanceService` |
| C3 | Late / missed check-out | Missing | Implemented (schedule + grace) | WRITTEN | `ScheduleService`, `AttendanceService` |
| C4 | Correction requests + approval | Missing | Implemented (submit/approve/reject, scoped reviewers) | WRITTEN | `AttendanceCorrectionService` |
| C5 | Schedules, holidays, time zones | Missing | One company schedule, holiday calendar, configurable zone, UTC storage | WRITTEN | `ScheduleService`, `HolidayService` |
| D1 | Leave submit/history/balances | Broken (mock) | Implemented | WRITTEN | `LeaveService` |
| D2 | Leave types & entitlements | Missing | Seeded ANNUAL/SICK/CASUAL, admin-editable | WRITTEN; seed EXEC (migration) | `V2`, `LeaveService.updateType` |
| D3 | Validation, overlap, balance rules | Missing | Implemented (see README rules) | WRITTEN | `LeaveService.submit` |
| D4 | Approve / reject, reviewer + timestamp | Broken (mock) | Implemented, never self, scoped, note required for reject | WRITTEN | `LeaveService` |
| D5 | Cancel / withdraw | Missing | Implemented | WRITTEN | `LeaveService.cancel` |
| D6 | Notifications & dashboard integration | Missing | Implemented | WRITTEN | `LeaveService`, `DashboardService` |
| E1 | Departments, assignment, safe delete | Missing | Implemented (reassign-then-delete) | WRITTEN | `DepartmentService` |
| F1 | Persistent notifications, unread count, read/read-all, ownership | Broken (browser memory) | Implemented, `findByIdAndUserId` ownership | WRITTEN, UI EXEC | `NotificationService` |
| G1 | Audit logging + admin viewer, filters, immutability, no secrets | Missing | Implemented; repository has no update/delete | WRITTEN (`PlatformIntegrationTest`, `DomainRulesTest`), UI EXEC | `AuditService` |
| H1 | Real dashboards (admin/manager/employee) | Broken (hardcoded) | Implemented from DB | WRITTEN, UI EXEC | `DashboardService` |
| H2 | Reports + CSV export | Missing | Implemented, scoped, formula-injection safe | CSV escaping **EXEC**; rest WRITTEN | `ReportService`, `CsvWriter` |
| H3 | Loading / empty / error states, no fabricated data | Broken (mock fallback) | Implemented everywhere | **EXEC** (jsdom, all pages) | `ui.js`, page scripts |
| I1 | Holiday calendar | Missing | Implemented | WRITTEN | `HolidayService` |
| I2 | Configurable schedules | Missing | Implemented (single schedule) | WRITTEN | `ScheduleService` |
| I3 | Employee directory | Missing | Implemented (employees page) | WRITTEN | |
| I4 | Exportable reports | Missing | Implemented | WRITTEN | |
| I5 | E-mail notifications | Missing | **Not implemented** (no reliable mail config) | — | — |
| I6 | Health checks | Missing | `/actuator/health` | WRITTEN | `application.properties` |
| I7 | Backup/restore docs | Missing | `docs/OPERATIONS.md` | — | |
| I8 | API documentation | Missing | `docs/API.md` + Postman collection (55 requests) | JSON validity **EXEC** | |
| I9 | CSV employee import | Missing | **Deferred** (justified: admin UI creation covers the need; adds validation/security surface) | — | — |
| I10 | Responsive/accessible UI | Partially | Existing responsive CSS kept; labelled controls, keyboard-operable dialogs (Esc, focus), `aria` labels | UI EXEC (structure only; no screen-reader test) | `ui.js` |
| — | Flyway migrations | Missing | V1–V4; upgrade-safe | **EXEC** on MySQL + H2 | `db/migration` |
| — | Dockerfile / Compose / CI / Dependabot | Missing | Added | YAML validity **EXEC**; not run | repo root, `.github` |

## Acceptance criteria (by area)

* **Auth:** bad credentials always yield `401 "Invalid email or password"`; 5 failures → `429`; logout/deactivation/password
  change make the old session return `401`; `role` in a public registration is ignored.
* **Authorization:** every endpoint has `@PreAuthorize`; employee A can never read or change employee B's leave,
  attendance, notifications or private profile fields; managers act only on direct reports; nobody reviews their own request.
* **Attendance:** second check-in the same day → `409`; check-out without check-in → `409`; late = after start + grace on a
  working day; summaries never mark today absent.
* **Leave:** weekends/holidays excluded; overlap → `409`; insufficient balance → `400`; pending days reserved; cancel
  restores balance; approve/reject records reviewer and time and notifies the employee.
* **Audit:** admin-only; filterable; contains no passwords/session ids; no API or repository method can modify rows.
* **Data:** all schema changes via Flyway; migrations run from a clean database and from an old Hibernate-created database
  without data loss.
