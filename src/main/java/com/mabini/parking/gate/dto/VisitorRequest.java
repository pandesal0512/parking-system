package com.mabini.parking.gate.dto;

import com.mabini.parking.common.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A visitor has no account. Security records the name and the vehicle type (plate is optional). */
public record VisitorRequest(

        @NotBlank(message = "The visitor's name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name,

        @NotNull(message = "Vehicle type is required (MOTORCYCLE, CAR or TRICYCLE).")
        VehicleType vehicleType,

        @Size(max = 30, message = "Plate number is too long.")
        String identifier) {
}
