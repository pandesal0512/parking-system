package com.mabini.parking.gate.dto;

import com.mabini.parking.common.enums.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What arrives at the gate: a plate and a direction. Today a staff member types it in (the gate
 * simulator). Later a camera device can send the same request.
 */
public record GateRequest(

        @NotBlank(message = "Plate number is required.")
        @Size(max = 30, message = "Plate number is too long.")
        String identifier,

        @NotNull(message = "Direction is required (IN or OUT).")
        Direction direction) {
}
