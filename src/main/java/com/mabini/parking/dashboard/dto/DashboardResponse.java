package com.mabini.parking.dashboard.dto;

import com.mabini.parking.common.enums.VehicleType;
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
        List<LogResponse> recentLogs,
        List<DeniedResponse> recentDenied) {

    /**
     * inside: vehicles of this type in the lot right now (from the entry/exit log).
     * reservedNow: reservations active at this moment.
     */
    public record TypeStatus(
            VehicleType vehicleType,
            int capacity,
            long inside,
            long available,
            long reservedNow) {
    }
}
