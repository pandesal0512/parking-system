package com.mabini.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed access to the "app.*" settings in application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue SeedAdmin seedAdmin,
        @DefaultValue Lockout lockout,
        @DefaultValue VehicleRules vehicle,
        @DefaultValue ReservationRules reservation) {

    public record Jwt(
            @DefaultValue("") String secret,
            @DefaultValue("60") long expirationMinutes) {
    }

    public record SeedAdmin(
            @DefaultValue("superadmin") String username,
            @DefaultValue("System Administrator") String fullName,
            @DefaultValue("") String password) {
    }

    public record Lockout(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15") long lockMinutes) {
    }

    /** autoApprove: true = new vehicles are approved immediately, false = a manager must approve. */
    public record VehicleRules(
            @DefaultValue("false") boolean autoApprove) {
    }

    /**
     * maxHours: longest single reservation.
     * maxDaysAhead: how far in advance a reservation can be made.
     * graceMinutes: how early a vehicle may enter before its reservation starts
     *               (also how late it may leave before counting as an overstay).
     * cancelCutoffMinutes: a user can cancel until this many minutes before the start.
     */
    public record ReservationRules(
            @DefaultValue("10") int maxHours,
            @DefaultValue("7") int maxDaysAhead,
            @DefaultValue("15") int graceMinutes,
            @DefaultValue("60") int cancelCutoffMinutes) {
    }
}
