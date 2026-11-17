# WorkPulse — Workforce Operations Platform

WorkPulse is a self-hosted web application for everyday people operations: employee directory, attendance tracking, leave management, hierarchical approvals, notifications, audit trail, dashboards, and exportable reports.

---

## Features

| Area | Included Functionality |
|---|---|
| **Accounts & Auth** | Login/logout with server-side sessions, BCrypt passwords, strong-password policy (12–128 chars), login throttling, profile edit, password change (ends all active sessions), admin password reset, optional self-registration (disabled by default). |
| **Role-Based Access** | `ADMIN`, `MANAGER` (reviews direct reports), `EMPLOYEE`. Role assignment is strictly administrator-managed. |
| **Employees** | Directory with search, department/status filters, sorting, and pagination; admin create/edit/activate/deactivate, role change, manager assignment (loop-safe); phone and role visibility restricted to the employee, their direct manager, and administrators. |
| **Departments** | CRUD, employee counts, safe deletion (reassign direct employees prior to deletion). |
| **Attendance** | Check-in/out, today's status, history with date ranges, daily/weekly/monthly summaries computed from stored records, late arrival detection, missed check-out detection, team/company roster, correction requests with manager/admin approval. |
| **Calendar & Schedules** | Company holiday calendar, configurable work schedule (hours, grace period, working days), configurable business time zone. |
| **Leave Management** | Seeded leave types and yearly entitlements (`ANNUAL`, `SICK`, `CASUAL`), balances, requests with overlap and balance validation, approve/reject with mandatory rejection notes, cancel/withdraw, working-day counting (weekends and holidays excluded). |
| **Notifications** | Stored in MySQL database, unread count badge, mark one/all read, server-side recipient ownership checks. |
| **Audit Log** | Append-only record of security and business events, admin-only viewer with filters and pagination; repository exposes no update or delete operations. |
| **Dashboards & Reports** | Real workforce metrics (Admin: company-wide, Manager: team, Employee: personal), filterable attendance and leave reports, formula-injection safe CSV export. |
| **Operations** | Flyway migrations, `/actuator/health`, Docker/Compose setup, GitHub Actions CI workflow, Postman test collection. |

---

## Technology and Architecture

- **Runtime & Language**: Java 17 / 21 LTS
- **Framework**: Spring Boot 3.3.4
- **Security**: Spring Security (HttpSession + Cookie CSRF + Method Security)
- **Persistence**: Spring Data JPA / Hibernate 6, Flyway Migrations
- **Database**: MySQL 8.0 (In-memory H2 in MySQL compatibility mode for unit/integration tests)
- **Build Tool**: Apache Maven (via `./mvnw` / `mvnw.cmd`)
- **Frontend**: Plain HTML5, CSS3, Vanilla JavaScript (vanilla DOM construction with `textContent` to prevent XSS, no external build pipeline required)

```
Browser (static HTML/JS, same origin)  ──/api/*──►  Controllers (@PreAuthorize on every method)
                                                      │ DTOs + Jakarta Validation
                                                      ▼
                                         Services (business rules, transactions, access checks, audit)
                                                      │
                                                      ▼
                                         Repositories (Spring Data JPA) ──► MySQL 8 (schema owned by Flyway)
```

- `com.workpulse.controller` — REST endpoints with explicit method-level authorization.
- `com.workpulse.service` — Core business workflows; `AccessService` centralizes authorization and IDOR ownership checks.
- `com.workpulse.entity` / `com.workpulse.repository` — JPA domain model; `AuditLogRepository` exposes no update/delete operations.
- `com.workpulse.dto` — Request/response records preventing entity mass assignment.
- `com.workpulse.security` / `com.workpulse.config` — Security filter chain, CSRF tokens, session lifecycle, login rate limiting.
- `src/main/resources/db/migration` — Versioned Flyway migrations (`V1` through `V4`).
- `src/main/resources/static` — Client UI assets.

---

## Prerequisites

- **Java Development Kit (JDK)**: JDK 17 or JDK 21 LTS
- **Database**: MySQL 8.x instance (or Docker)
- **Maven**: Maven wrapper (`./mvnw` or `mvnw.cmd`) included

---

## Configuration

All configuration is provided via environment variables (see [`.env.example`](.env.example)). There are **no hardcoded credentials or default admin accounts**.

| Variable | Description | Default |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | MySQL host, port, database name | `localhost`, `3306`, `workpulse_db` |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL database credentials | **Required** |
| `WORKPULSE_ADMIN_EMAIL`, `WORKPULSE_ADMIN_PASSWORD`, `WORKPULSE_ADMIN_NAME` | Initial administrator bootstrap credentials | Unset |
| `APP_TIMEZONE` | Business time zone (used for today cutoffs, attendance, and leaves) | `Asia/Kolkata` |
| `REGISTRATION_ENABLED` | Public registration toggle (always creates `EMPLOYEE`) | `false` |
| `COOKIE_SECURE` | Sets `Secure` attribute on session cookie (enable when served over HTTPS) | `false` |
| `DB_USE_SSL`, `DB_ALLOW_PUBLIC_KEY_RETRIEVAL` | JDBC SSL and public key retrieval flags | `false`, `false` |
| `PORT` | HTTP server port | `8080` |

### Database Setup

```sql
CREATE DATABASE workpulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'workpulse'@'%' IDENTIFIED BY '<strong-password>';
GRANT ALL PRIVILEGES ON workpulse_db.* TO 'workpulse'@'%';
```

Flyway automatically applies all schema migrations on first startup (`spring.jpa.hibernate.ddl-auto=none`).

---

## Deployment Behind a Reverse Proxy

When running behind a reverse proxy (e.g., Nginx, AWS ALB, Caddy):
1. **Forward Headers Strategy**:
   - Default: `server.forward-headers-strategy=none`. Under this setting, client IP used for login throttling and audit logging comes directly from the socket connection (`127.0.0.1`), ignoring spoofed `X-Forwarded-For` headers.
   - When deployed behind a trusted proxy, set `server.forward-headers-strategy=framework` and configure trusted proxy CIDRs in Spring Boot so that legitimate forwarded headers are extracted safely.
2. **HTTPS & Cookie Security**:
   - Ensure the proxy sets `X-Forwarded-Proto: https` and set `COOKIE_SECURE=true` so session cookies carry the `Secure` flag.

---

## How to Run

### Development Mode

```bash
export DB_USERNAME=root DB_PASSWORD=<password>
export WORKPULSE_ADMIN_EMAIL=admin@example.com WORKPULSE_ADMIN_PASSWORD='Str0ng!Passphrase#1'
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production Mode

```bash
./mvnw clean package -DskipTests
java -jar target/workpulse-1.0.0-SNAPSHOT.jar
```

---

## How to Run Tests

### In-Memory H2 Tests
```bash
./mvnw clean verify
```
Runs the full 94-test suite using in-memory H2 in MySQL mode with schema created by the Flyway migrations.

### Real MySQL 8 Verification
Set the MySQL datasource environment variables matching CI:
```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/workpulse_ci?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="<password>"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
./mvnw clean verify
```

---

## Known Limitations

- **In-Memory Login Throttling & Sessions**: Rate limiting and HTTP sessions are maintained in JVM memory. In multi-instance or clustered deployments, rate limiting and sessions are not shared across nodes. Horizontal scaling requires Redis/Spring Session and a distributed rate limiter.
- **Whole-Day Leave Increments**: Leave requests operate strictly on full-day integer increments. Partial-day or half-day leaves are not supported in the current schema.
- **No Self-Service Password Reset**: Because external email delivery is not configured, password resets are administrator-initiated.
- **In-App Notifications Only**: Notifications are stored in the database; external SMS/email dispatch is not implemented.
- **Local Environment Constraints**: Local container verification (`docker`) and JavaScript syntax checking (`node --check`) were blocked due to missing CLI tools on the development host; remote GitHub Actions CI is unverified until executed on the remote runner.

---

## Screenshots

To capture screenshots from the running application:
1. Start the application locally with the `dev` profile.
2. Open `http://localhost:8080` in your web browser.
3. Capture the following views and save them to `docs/screenshots/`:
   - `login.png`: Login screen (`/login.html`)
   - `dashboard.png`: Admin / Manager dashboard (`/index.html`)
   - `attendance.png`: Attendance check-in and history view (`/attendance.html`)
   - `leaves.png`: Leave balance and application form (`/leaves.html`)
   - `approvals.png`: Manager leave approval modal (`/leaves.html` review tab)
   - `employees.png`: Employee directory table (`/employees.html`)

*(Screenshots will be committed to `docs/screenshots/` upon UI capture).*
