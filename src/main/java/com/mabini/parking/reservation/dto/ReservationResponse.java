package com.mabini.parking.reservation.dto;

import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.reservation.Reservation;

import java.time.LocalDateTime;

/** Call from() only inside a transaction: it reads lazily loaded user and vehicle. */
public record ReservationResponse(
        Long id,
        String ownerName,
        String ownerUsername,
        String identifier,
        VehicleType vehicleType,
        LocalDateTime start,
        LocalDateTime end,
        ReservationStatus status,
        boolean cancellable,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String reviewedBy,
        String reviewNote) {

    public static ReservationResponse from(Reservation r, boolean cancellable) {
        return new ReservationResponse(
                r.getId(),
                r.getUser().getFullName(),
                r.getUser().getUsername(),
                r.getVehicle().getIdentifier(),
                r.getVehicleType(),
                r.getStartTime(),
                r.getEndTime(),
                r.getStatus(),
                cancellable,
                r.getCreatedAt(),
                r.getCancelledAt(),
                r.getReviewedBy() == null ? null : r.getReviewedBy().getFullName(),
                r.getReviewNote());
    }
}
