package com.mabini.parking.auth;

import com.mabini.parking.common.enums.Role;

/** The logged-in person for the current request. Controllers receive it with @AuthenticationPrincipal. */
public record AuthUser(Long id, String username, Role role) {
}
