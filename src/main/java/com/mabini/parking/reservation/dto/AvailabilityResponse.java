package com.mabini.parking.reservation.dto;

import com.mabini.parking.common.enums.VehicleType;

import java.time.LocalDateTime;

/** "peak" is the most spaces already reserved at any one moment in the window. */
public record AvailabilityResponse(
        VehicleType vehicleType,
        LocalDateTime start,
        LocalDateTime end,
        int capacity,
        int reserved,
        int remaining) {
}
