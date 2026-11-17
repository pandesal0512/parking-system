package com.mabini.parking.dashboard;

import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.dashboard.dto.DashboardResponse;
import com.mabini.parking.log.AccessLogRepository;
import com.mabini.parking.log.DeniedAttemptRepository;
import com.mabini.parking.log.LogService;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private static final int RECENT = 10;

    private final ParkingCapacityRepository capacities;
    private final AccessLogRepository logs;
    private final DeniedAttemptRepository denied;
    private final ReservationRepository reservations;
    private final VehicleRepository vehicles;
    private final LogService logService;
    private final Clock clock;

    public DashboardService(ParkingCapacityRepository capacities, AccessLogRepository logs,
                            DeniedAttemptRepository denied, ReservationRepository reservations,
                            VehicleRepository vehicles, LogService logService, Clock clock) {
        this.capacities = capacities;
        this.logs = logs;
        this.denied = denied;
        this.reservations = reservations;
        this.vehicles = vehicles;
        this.logService = logService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<DashboardResponse.TypeStatus> types = new ArrayList<>();
        for (VehicleType type : VehicleType.values()) {
            int capacity = capacities.findById(type).map(ParkingCapacity::getCapacity).orElse(0);
            long inside = logs.countByVehicleTypeAndExitTimeIsNull(type);
            long reservedNow = reservations
                    .findOverlapping(type, ReservationStatus.ACTIVE, now, now.plusMinutes(1)).size();
            types.add(new DashboardResponse.TypeStatus(type, capacity, inside,
                    Math.max(0L, capacity - inside), reservedNow));
        }

        return new DashboardResponse(
                types,
                reservations.countByStatusAndStartTimeBetween(ReservationStatus.ACTIVE, dayStart, dayEnd),
                logs.countByEntryTimeBetween(dayStart, dayEnd),
                denied.countByAttemptedAtBetween(dayStart, dayEnd),
                vehicles.countByStatus(VehicleStatus.PENDING),
                logService.recent(RECENT),
                logService.recentDenied(RECENT));
    }
}
