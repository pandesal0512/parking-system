package com.mabini.parking.vehicle.dto;

import com.mabini.parking.common.enums.UserType;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.user.User;
import com.mabini.parking.vehicle.Vehicle;

import java.time.LocalDateTime;

/** Call from() only inside a transaction: it reads the (lazily loaded) owner. */
public record VehicleResponse(
        Long id,
        String ownerName,
        String ownerUsername,
        UserType ownerType,
        VehicleType vehicleType,
        String identifier,
        String make,
        String model,
        String color,
        VehicleStatus status,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt) {

    public static VehicleResponse from(Vehicle v) {
        User owner = v.getOwner();
        return new VehicleResponse(
                v.getId(),
                owner.getFullName(),
                owner.getUsername(),
                owner.getUserType(),
                v.getVehicleType(),
                v.getIdentifier(),
                v.getMake(),
                v.getModel(),
                v.getColor(),
                v.getStatus(),
                v.getReviewedAt(),
                v.getCreatedAt());
    }
}
