package com.mabini.parking.common.enums;

public enum ReservationStatus {
    /** Asked for by the user, waiting for a manager. Holds no space yet. */
    PENDING,
    ACTIVE,
    /** A manager said no. */
    REJECTED,
    /** Nobody reviewed it before its start time. */
    EXPIRED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /** Reservations that are still alive: waiting for approval, or approved and not yet used up. */
    public static final java.util.List<ReservationStatus> OPEN = java.util.List.of(PENDING, ACTIVE);
}
