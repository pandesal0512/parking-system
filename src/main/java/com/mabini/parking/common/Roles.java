package com.mabini.parking.common;

/** Ready-made access rules for @PreAuthorize, so every endpoint spells them the same way. */
public final class Roles {

    /** Parking manager or super admin. */
    public static final String STAFF = "hasAnyRole('PARKING_MANAGER','SUPER_ADMIN')";

    /** Regular users only (students, faculty, personnel). */
    public static final String USER_ONLY = "hasRole('USER')";

    /** The gate hardware (ESP32), authenticated by its device key. Not a person. */
    public static final String DEVICE_ONLY = "hasRole('DEVICE')";

    public static final String SUPER_ADMIN_ONLY = "hasRole('SUPER_ADMIN')";

    private Roles() {
    }
}
