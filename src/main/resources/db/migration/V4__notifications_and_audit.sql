-- V4: persistent notifications and audit log

CREATE TABLE notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    link       VARCHAR(200) NULL,
    is_read    BOOLEAN      NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);

-- actor_email is stored as text (not a foreign key) so history survives account changes.
CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    actor_email   VARCHAR(150)  NULL,
    action        VARCHAR(50)   NOT NULL,
    resource_type VARCHAR(50)   NOT NULL,
    resource_id   VARCHAR(50)   NULL,
    details       VARCHAR(1000) NULL,
    ip_address    VARCHAR(64)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
CREATE INDEX idx_audit_action ON audit_logs (action);
