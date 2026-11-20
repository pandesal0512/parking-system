-- A reservation reserves ONE space of the vehicle's type for a time window.
-- vehicle_type is copied from the vehicle so capacity counting is a simple query.
CREATE TABLE reservations (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    vehicle_id    BIGINT       NOT NULL,
    vehicle_type  VARCHAR(20)  NOT NULL,
    start_time    DATETIME(6)  NOT NULL,
    end_time      DATETIME(6)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    cancelled_at  DATETIME(6)  NULL,
    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT ck_reservations_window CHECK (end_time > start_time)
);

CREATE INDEX idx_reservations_window ON reservations (vehicle_type, status, start_time, end_time);
CREATE INDEX idx_reservations_user ON reservations (user_id, status);
