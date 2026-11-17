package com.mabini.parking.common;

/** Ready-made access rules for @PreAuthorize, so every endpoint spells them the same way. */
public final class Roles {

    /** Parking manager or super admin. */
    public static final String STAFF = "hasAnyRole('PARKING_MANAGER','SUPER_ADMIN')";

    /** Regular users only (students, faculty, deans, personnel). */
    public static final String USER_ONLY = "hasRole('USER')";

    public static final String SUPER_ADMIN_ONLY = "hasRole('SUPER_ADMIN')";

    private Roles() {
    }
}
