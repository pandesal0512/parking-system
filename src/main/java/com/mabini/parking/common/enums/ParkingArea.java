package com.mabini.parking.common.enums;

/**
 * Which part of the lot a vehicle uses. Capacity is counted separately for each area.
 * GENERAL: students, visitors (and anyone whose own area has no spaces set).
 * FACULTY: the parking reserved for faculty and personnel.
 */
public enum ParkingArea {
    GENERAL,
    FACULTY;

    /** Faculty and personnel may use the faculty area; everyone else (students, staff accounts) the general lot. */
    public static boolean isFacultyType(UserType type) {
        return type == UserType.FACULTY || type == UserType.PERSONNEL;
    }
}
