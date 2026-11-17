package com.mabini.parking.log.dto;

import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.log.AccessLog;
import com.mabini.parking.vehicle.Vehicle;

import java.time.LocalDateTime;

/** Call from() only inside a transaction: it reads the lazily loaded vehicle and owner. */
public record LogResponse(
        Long id,
        String identifier,
        String name,
        boolean visitor,
        VehicleType vehicleType,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        boolean overstay) {

    public static LogResponse from(AccessLog log) {
        Vehicle vehicle = log.getVehicle();
        boolean visitor = (vehicle == null);
        String name = visitor ? log.getVisitorName() : vehicle.getOwner().getFullName();
        return new LogResponse(
                log.getId(),
                log.getIdentifierRead(),
                name,
                visitor,
                log.getVehicleType(),
                log.getEntryTime(),
                log.getExitTime(),
                log.isOverstay());
    }
}
