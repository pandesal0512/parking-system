package com.mabini.parking.vehicle.dto;

import com.mabini.parking.common.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What a user sends to register or update their vehicle. Status and owner are never accepted here. */
public record VehicleRequest(

        @NotNull(message = "Vehicle type is required (MOTORCYCLE, CAR or TRICYCLE).")
        VehicleType vehicleType,

        @NotBlank(message = "Plate number is required.")
        @Size(max = 30, message = "Plate number is too long.")
        String identifier,

        @Size(max = 50, message = "Make must be at most 50 characters.")
        String make,

        @Size(max = 50, message = "Model must be at most 50 characters.")
        String model,

        @Size(max = 30, message = "Color must be at most 30 characters.")
        String color) {
}
