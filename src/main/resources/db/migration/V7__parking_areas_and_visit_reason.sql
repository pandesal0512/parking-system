-- Faculty, dean and personnel have their own parking area (about 12 spaces), separate from the
-- general lot. Capacity is now kept per AREA and vehicle type, and every reservation and visit
-- remembers which area it used so the counts stay separate.
-- The capacity table is rebuilt with an id, because its key now has two parts.
CREATE TABLE parking_capacity_v2 (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    area         VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    capacity     INT         NOT NULL,
    CONSTRAINT pk_parking_capacity_v2 PRIMARY KEY (id),
    CONSTRAINT uq_parking_capacity_area_type UNIQUE (area, vehicle_type),
    CONSTRAINT ck_parking_capacity_v2_positive CHECK (capacity >= 0)
);

INSERT INTO parking_capacity_v2 (area, vehicle_type, capacity)
SELECT 'GENERAL', vehicle_type, capacity FROM parking_capacity;

-- Faculty area: about 12 car spaces (estimate, the super admin can change it).
-- A capacity of 0 here means "no separate area for that type": those vehicles use the general lot.
INSERT INTO parking_capacity_v2 (area, vehicle_type, capacity) VALUES ('FACULTY', 'CAR', 12);
INSERT INTO parking_capacity_v2 (area, vehicle_type, capacity) VALUES ('FACULTY', 'MOTORCYCLE', 0);
INSERT INTO parking_capacity_v2 (area, vehicle_type, capacity) VALUES ('FACULTY', 'TRICYCLE', 0);

DROP TABLE parking_capacity;
ALTER TABLE parking_capacity_v2 RENAME TO parking_capacity;

ALTER TABLE reservations ADD COLUMN area VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE access_logs ADD COLUMN area VARCHAR(20) NOT NULL DEFAULT 'GENERAL';

-- Why a visitor came (required for visitors; empty for members).
ALTER TABLE access_logs ADD COLUMN visit_reason VARCHAR(255) NULL;
