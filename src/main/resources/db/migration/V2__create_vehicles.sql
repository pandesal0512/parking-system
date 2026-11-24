-- One vehicle per user (owner_id is unique). "identifier" is the plate number
-- (or body number for e-trikes without a plate), stored uppercase without spaces.
CREATE TABLE vehicles (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id      BIGINT       NOT NULL,
    vehicle_type  VARCHAR(20)  NOT NULL,
    identifier    VARCHAR(30)  NOT NULL,
    make          VARCHAR(50)  NULL,
    model         VARCHAR(50)  NULL,
    color         VARCHAR(30)  NULL,
    status        VARCHAR(20)  NOT NULL,
    reviewed_by   BIGINT       NULL,
    reviewed_at   DATETIME(6)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_vehicles PRIMARY KEY (id),
    CONSTRAINT uq_vehicles_owner UNIQUE (owner_id),
    CONSTRAINT uq_vehicles_identifier UNIQUE (identifier),
    CONSTRAINT fk_vehicles_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_vehicles_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
);
