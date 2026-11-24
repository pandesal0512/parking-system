-- Capacity per vehicle type (we count vehicles, not individual slots).
CREATE TABLE parking_capacity (
    vehicle_type VARCHAR(20) NOT NULL,
    capacity     INT         NOT NULL,
    CONSTRAINT pk_parking_capacity PRIMARY KEY (vehicle_type),
    CONSTRAINT ck_parking_capacity_positive CHECK (capacity >= 0)
);

-- Starting values from the capacity survey. The motorcycle figure is an estimate,
-- so the super admin should be able to change these later.
INSERT INTO parking_capacity (vehicle_type, capacity) VALUES ('MOTORCYCLE', 60);
INSERT INTO parking_capacity (vehicle_type, capacity) VALUES ('CAR', 33);
INSERT INTO parking_capacity (vehicle_type, capacity) VALUES ('TRICYCLE', 8);
