package com.mabini.parking.capacity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CapacityRequest(

        @NotNull(message = "Capacity is required.")
        @Min(value = 0, message = "Capacity cannot be negative.")
        @Max(value = 1000, message = "Capacity cannot be more than 1000.")
        Integer capacity) {
}
