package com.mabini.parking.common.enums;

/** Why the gate refused a vehicle. Stored with every denied attempt. */
public enum DenyReason {
    UNREGISTERED,
    VEHICLE_NOT_APPROVED,
    ACCOUNT_DISABLED,
    ACCOUNT_SUSPENDED,
    ALREADY_INSIDE,
    NO_RESERVATION,
    LOT_FULL
}
