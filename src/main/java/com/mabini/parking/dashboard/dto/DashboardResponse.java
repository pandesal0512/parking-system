package com.mabini.parking.dashboard.dto;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.device.dto.DeviceStatus;
import com.mabini.parking.log.dto.DeniedResponse;
import com.mabini.parking.log.dto.LogResponse;

import java.util.List;

/** Everything the monitoring dashboard shows, in one response. */
public record DashboardResponse(
        List<TypeStatus> types,
        long reservationsToday,
        long entriesToday,
        long deniedToday,
        long pendingVehicles,
        long pendingReservations,
        long overstayingNow,
        List<DeviceStatus> devices,
        List<LogResponse> recentLogs,
        List<DeniedResponse> recentDenied) {

    /**
     * One row per area and vehicle type that has spaces (or vehicles inside).
     * inside: vehicles of this type in the area right now (from the entry/exit log).
     * reservedNow: reservations active at this moment.
     */
    public record TypeStatus(
            ParkingArea area,
            VehicleType vehicleType,
            int capacity,
            long inside,
            long available,
            long reservedNow) {
    }
}
