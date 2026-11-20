package com.mabini.parking.reservation.dto;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;

import java.time.LocalDateTime;

/** "reserved" is the most spaces already reserved at any one moment in the window, in this area. */
public record AvailabilityResponse(
        ParkingArea area,
        VehicleType vehicleType,
        LocalDateTime start,
        LocalDateTime end,
        int capacity,
        int reserved,
        int remaining) {
}
