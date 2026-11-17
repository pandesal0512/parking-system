package com.mabini.parking.auth.dto;

import com.mabini.parking.common.enums.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a new person sends to register.
 * There is deliberately NO "role" field: anyone registering becomes a plain USER, and even if
 * someone adds "role" to the JSON, it is ignored.
 */
public record RegisterRequest(

        @NotBlank(message = "Username is required.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{4,30}$",
                message = "Username must be 4-30 characters: letters, numbers, dot, underscore or dash.")
        String username,

        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters.")
        String fullName,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters.")
        String password,

        @NotNull(message = "User type is required (STUDENT, FACULTY, DEAN or PERSONNEL).")
        UserType userType,

        @Size(max = 30, message = "School ID must be at most 30 characters.")
        String schoolId) {
}
