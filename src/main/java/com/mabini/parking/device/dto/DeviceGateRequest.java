package com.mabini.parking.device.dto;

import com.mabini.parking.common.enums.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What the gate hardware sends when it has read a plate.
 * direction is fixed by where the device is mounted: IN at the entrance, OUT at the exit.
 */
public record DeviceGateRequest(

        @NotBlank(message = "deviceId is required.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,40}$",
                message = "deviceId may contain letters, numbers, dot, underscore and dash (max 40).")
        String deviceId,

        @NotBlank(message = "Plate number is required.")
        @Size(max = 30, message = "Plate number is too long.")
        String identifier,

        @NotNull(message = "Direction is required (IN or OUT).")
        Direction direction) {
}
