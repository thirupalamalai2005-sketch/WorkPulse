-- V3: attendance, attendance corrections, leave requests

CREATE TABLE attendance_records (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    employee_id  BIGINT      NOT NULL,
    work_date    DATE        NOT NULL,
    check_in_at  DATETIME(6) NOT NULL,
    check_out_at DATETIME(6) NULL,
    late         BOOLEAN     NOT NULL,
    source       VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- one record per employee per day: prevents duplicate check-ins at database level
    CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
);
CREATE INDEX idx_attendance_work_date ON attendance_records (work_date);

CREATE TABLE attendance_corrections (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id        BIGINT       NOT NULL,
    work_date          DATE         NOT NULL,
    requested_check_in  DATETIME(6) NULL,
    requested_check_out DATETIME(6) NULL,
    reason             VARCHAR(500) NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    reviewer_id        BIGINT       NULL,
    reviewed_at        DATETIME(6)  NULL,
    review_note        VARCHAR(500) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_corrections_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_corrections_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees (id)
);
CREATE INDEX idx_corrections_status ON attendance_corrections (status);

CREATE TABLE leave_requests (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT       NOT NULL,
    leave_type_id BIGINT       NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    day_count     INT          NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    reviewer_id   BIGINT       NULL,
    reviewed_at   DATETIME(6)  NULL,
    review_note   VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    cancelled_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_types (id),
    CONSTRAINT fk_leave_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees (id),
    CONSTRAINT ck_leave_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_leave_employee_start ON leave_requests (employee_id, start_date);
CREATE INDEX idx_leave_status ON leave_requests (status);
