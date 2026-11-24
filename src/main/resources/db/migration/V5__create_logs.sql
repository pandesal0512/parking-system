-- Entry/exit log book. Covers registered vehicles AND visitors.
-- A row with exit_time = NULL means the vehicle is currently inside the parking lot.
CREATE TABLE access_logs (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_id       BIGINT       NULL,
    identifier_read  VARCHAR(30)  NULL,
    visitor_name     VARCHAR(120) NULL,
    vehicle_type     VARCHAR(20)  NOT NULL,
    reservation_id   BIGINT       NULL,
    entry_time       DATETIME(6)  NOT NULL,
    exit_time        DATETIME(6)  NULL,
    is_override      BOOLEAN      NOT NULL DEFAULT FALSE,
    override_reason  VARCHAR(255) NULL,
    overstay         BOOLEAN      NOT NULL DEFAULT FALSE,
    recorded_by      BIGINT       NULL,
    CONSTRAINT pk_access_logs PRIMARY KEY (id),
    CONSTRAINT fk_access_logs_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_access_logs_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_access_logs_recorder FOREIGN KEY (recorded_by) REFERENCES users (id)
);

CREATE INDEX idx_access_logs_inside ON access_logs (vehicle_type, exit_time);
CREATE INDEX idx_access_logs_entry ON access_logs (entry_time);

-- Every refused entry attempt.
CREATE TABLE denied_attempts (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    identifier_read  VARCHAR(30) NULL,
    reason           VARCHAR(40) NOT NULL,
    attempted_at     DATETIME(6) NOT NULL,
    recorded_by      BIGINT      NULL,
    CONSTRAINT pk_denied_attempts PRIMARY KEY (id),
    CONSTRAINT fk_denied_attempts_recorder FOREIGN KEY (recorded_by) REFERENCES users (id)
);

CREATE INDEX idx_denied_attempts_time ON denied_attempts (attempted_at);
