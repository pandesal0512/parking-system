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
        @DefaultValue ReservationRules reservation,
        @DefaultValue Penalty penalty,
        @DefaultValue Demo demo,
        @DefaultValue LogRetention logs,
        @DefaultValue Device device) {

    public record Jwt(
            @DefaultValue("") String secret,
            @DefaultValue("60") long expirationMinutes) {
    }

    public record SeedAdmin(
            @DefaultValue("superadmin") String username,
            @DefaultValue("System Administrator") String fullName,
            @DefaultValue("") String password,
            @DefaultValue("false") boolean resetPassword) {
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
     * maxPerDay: how many reservations one user can make for the same calendar day (stops spamming).
     * autoApprove: true = reservations are approved immediately, false = a manager must approve each one.
     */
    public record ReservationRules(
            @DefaultValue("10") int maxHours,
            @DefaultValue("7") int maxDaysAhead,
            @DefaultValue("15") int graceMinutes,
            @DefaultValue("60") int cancelCutoffMinutes,
            @DefaultValue("1") int maxPerDay,
            @DefaultValue("false") boolean autoApprove) {
    }

    /**
     * noShowBanThreshold: after this many no-shows the user may no longer reserve parking.
     * overstaySuspensionDays: how long a user cannot reserve or enter after an overstay.
     * jobEnabled / checkSeconds: the background check that finds no-shows and overstays.
     */
    public record Penalty(
            @DefaultValue("3") int noShowBanThreshold,
            @DefaultValue("3") int overstaySuspensionDays,
            @DefaultValue("true") boolean jobEnabled,
            @DefaultValue("60") int checkSeconds) {
    }

    /**
     * timeControl: lets the super admin move the system clock forward (for demos ONLY).
     * seedData: adds sample accounts, vehicles and reservations at startup (for demos ONLY).
     */
    public record Demo(
            @DefaultValue("false") boolean timeControl,
            @DefaultValue("false") boolean seedData) {
    }

    /**
     * apiKey: the secret the gate hardware sends in the X-Device-Key header. Empty means the hardware
     * endpoints are switched off. onlineSeconds: a device counts as online if it was heard within this time.
     */
    public record Device(
            @DefaultValue("") String apiKey,
            @DefaultValue("30") int onlineSeconds) {
    }

    /** Entry and exit logs older than this many days are deleted by the nightly cleanup. */
    public record LogRetention(
            @DefaultValue("30") int retentionDays,
            @DefaultValue("true") boolean cleanupEnabled) {
    }
}
