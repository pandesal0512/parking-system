-- History of warnings and penalties. The counters on the user row (warning_count, suspended_until,
-- registration_banned) are what the rules check; this table explains where they came from.
-- One penalty of each type per reservation, so the background check can never punish twice.
CREATE TABLE penalties (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    reservation_id  BIGINT       NULL,
    type            VARCHAR(20)  NOT NULL,
    description     VARCHAR(255) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_penalties PRIMARY KEY (id),
    CONSTRAINT fk_penalties_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_penalties_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT uq_penalties_reservation_type UNIQUE (reservation_id, type)
);

CREATE INDEX idx_penalties_user ON penalties (user_id, created_at);
