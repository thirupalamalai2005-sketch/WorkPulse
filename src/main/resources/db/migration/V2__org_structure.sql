-- V2: departments, employees, holidays, work schedule, leave types

-- Databases created by the old ddl-auto=update setup have users.role as a native MySQL ENUM('ADMIN','EMPLOYEE'),
-- which cannot store the new MANAGER role. Widen it to VARCHAR (values are preserved). No-op on clean databases.
ALTER TABLE users MODIFY COLUMN role VARCHAR(20) NOT NULL;

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name)
);

CREATE TABLE employees (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code VARCHAR(20)  NOT NULL,
    user_id       BIGINT       NOT NULL,
    department_id BIGINT       NULL,
    manager_id    BIGINT       NULL,
    designation   VARCHAR(100) NULL,
    phone         VARCHAR(20)  NULL,
    join_date     DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT uk_employees_user UNIQUE (user_id),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES employees (id)
);

-- Every pre-existing user becomes an employee (non-destructive backfill).
INSERT INTO employees (employee_code, user_id, join_date, status, created_at, updated_at)
SELECT CASE WHEN user_id < 10 THEN CONCAT('WP-000', user_id)
            WHEN user_id < 100 THEN CONCAT('WP-00', user_id)
            WHEN user_id < 1000 THEN CONCAT('WP-0', user_id)
            ELSE CONCAT('WP-', user_id) END,
       user_id,
       CAST(created_at AS DATE),
       CASE WHEN enabled = TRUE THEN 'ACTIVE' ELSE 'INACTIVE' END,
       created_at,
       created_at
FROM users;

CREATE TABLE holidays (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_holidays_date UNIQUE (holiday_date)
);

CREATE TABLE work_schedules (
    id            BIGINT       NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    grace_minutes INT          NOT NULL,
    working_days  VARCHAR(40)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO work_schedules (id, name, start_time, end_time, grace_minutes, working_days, updated_at)
VALUES (1, 'DEFAULT', '09:00:00', '18:00:00', 15, 'MON,TUE,WED,THU,FRI', CURRENT_TIMESTAMP);

CREATE TABLE leave_types (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    code               VARCHAR(30) NOT NULL,
    name               VARCHAR(60) NOT NULL,
    annual_entitlement INT         NOT NULL,
    allow_past_dates   BOOLEAN     NOT NULL,
    active             BOOLEAN     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_leave_types_code UNIQUE (code)
);

INSERT INTO leave_types (code, name, annual_entitlement, allow_past_dates, active) VALUES ('ANNUAL', 'Annual Leave', 18, FALSE, TRUE);
INSERT INTO leave_types (code, name, annual_entitlement, allow_past_dates, active) VALUES ('SICK', 'Sick Leave', 10, TRUE, TRUE);
INSERT INTO leave_types (code, name, annual_entitlement, allow_past_dates, active) VALUES ('CASUAL', 'Casual Leave', 6, FALSE, TRUE);
