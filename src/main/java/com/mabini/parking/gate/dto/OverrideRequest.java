package com.mabini.parking.gate.dto;

import com.mabini.parking.common.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A manual override entry. The reason is required. For a registered plate the vehicle type and
 * owner come from the registration; for an unknown plate the type is required.
 */
public record OverrideRequest(

        @NotBlank(message = "Enter the plate number.")
        @Size(max = 30, message = "Plate number is too long.")
        String identifier,

        VehicleType vehicleType,

        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name,

        @NotBlank(message = "A reason is required for a manual override.")
        @Size(min = 5, max = 255, message = "The reason must be 5-255 characters.")
        String reason) {
}
