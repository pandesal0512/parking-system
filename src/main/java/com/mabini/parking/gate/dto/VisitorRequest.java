package com.mabini.parking.gate.dto;

import com.mabini.parking.common.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A visitor has no account. Security records the name, the vehicle type, the plate number and
 * the reason for the visit. All four are required.
 */
public record VisitorRequest(

        @NotBlank(message = "The visitor's name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name,

        @NotNull(message = "Vehicle type is required (MOTORCYCLE, CAR or TRICYCLE).")
        VehicleType vehicleType,

        @NotBlank(message = "The plate number is required.")
        @Size(max = 30, message = "Plate number is too long.")
        String identifier,

        @NotBlank(message = "The reason for the visit is required.")
        @Size(min = 3, max = 255, message = "The reason must be 3-255 characters.")
        String reason) {
}
