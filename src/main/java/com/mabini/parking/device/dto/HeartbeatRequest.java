package com.mabini.parking.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record HeartbeatRequest(

        @NotBlank(message = "deviceId is required.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,40}$",
                message = "deviceId may contain letters, numbers, dot, underscore and dash (max 40).")
        String deviceId) {
}
