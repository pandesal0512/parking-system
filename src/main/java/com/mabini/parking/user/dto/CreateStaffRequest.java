package com.mabini.parking.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Super admin creates a parking manager. There is no password and no role in the request:
 * the system makes a temporary password, and the role is always PARKING_MANAGER.
 */
public record CreateStaffRequest(

        @NotBlank(message = "Username is required.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{4,30}$",
                message = "Username must be 4-30 characters: letters, numbers, dot, underscore or dash.")
        String username,

        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters.")
        String fullName) {
}
