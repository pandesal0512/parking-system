package com.mabini.parking.user.dto;

/** Returned once, right after creating an account or resetting a password. It is never stored in plain text. */
public record TemporaryPasswordResponse(UserResponse user, String temporaryPassword) {
}
