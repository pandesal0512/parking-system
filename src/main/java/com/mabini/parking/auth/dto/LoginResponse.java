package com.mabini.parking.auth.dto;

import com.mabini.parking.common.enums.Role;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Role role,
        boolean mustChangePassword) {
}
