package com.mabini.parking.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * A reservation request holds only the time window. The vehicle is the user's own
 * registered vehicle (taken from the login), so nobody can book for someone else's plate.
 */
public record CreateReservationRequest(

        @NotNull(message = "Start time is required.")
        LocalDateTime start,

        @NotNull(message = "End time is required.")
        LocalDateTime end) {
}
