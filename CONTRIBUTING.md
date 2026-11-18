# Contributing to WorkPulse

Thank you for your interest in contributing to WorkPulse!

## Code of Conduct

All contributors and maintainers are expected to maintain a professional, respectful, and collaborative environment.

## Development Setup

1. **Prerequisites**:
   - Java 17+ LTS
   - MySQL 8.x
   - Maven wrapper (`./mvnw` or `mvnw.cmd`)

2. **Clone and Build**:
   ```bash
   ./mvnw clean test-compile
   ./mvnw test
   ```

3. **Running Integration Tests**:
   - In-memory H2 tests run automatically with `./mvnw test` or `./mvnw verify`.
   - To run against a local MySQL 8 database, set the environment variables:
     ```bash
     export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/workpulse_ci?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
     export SPRING_DATASOURCE_USERNAME="root"
     export SPRING_DATASOURCE_PASSWORD="<password>"
     export SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver"
     ./mvnw verify
     ```

## Coding and Testing Guidelines

- **Architecture**: Keep controllers thin, centralize access and authorization in `AccessService`, encapsulate business logic in services, and enforce database schema ownership through Flyway migrations.
- **Testing**:
  - Every bug fix or new feature must include regression tests.
  - Concurrency tests must use bounded latches (`CountDownLatch`) and assert final database state.
  - Do not weaken assertions, bypass CSRF/authentication, or add arbitrary thread sleeps.
- **Style**:
  - Follow standard Java conventions.
  - Ensure zero compiler warnings and cleanly passing builds across all supported timezones.

## Submitting Pull Requests

1. Create a feature branch from `main`.
2. Ensure `./mvnw verify` passes cleanly with zero failures or skipped tests.
3. Open a Pull Request with a clear summary of the changes, motivation, and test evidence.
