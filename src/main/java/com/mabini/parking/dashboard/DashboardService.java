package com.mabini.parking.dashboard;

import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.dashboard.dto.DashboardResponse;
import com.mabini.parking.device.DeviceRegistry;
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
    private final DeviceRegistry deviceRegistry;
    private final Clock clock;

    public DashboardService(ParkingCapacityRepository capacities, AccessLogRepository logs,
                            DeniedAttemptRepository denied, ReservationRepository reservations,
                            VehicleRepository vehicles, LogService logService,
                            DeviceRegistry deviceRegistry, Clock clock) {
        this.capacities = capacities;
        this.logs = logs;
        this.denied = denied;
        this.reservations = reservations;
        this.vehicles = vehicles;
        this.logService = logService;
        this.deviceRegistry = deviceRegistry;
        this.clock = clock;
    }

    /** Spaces per area and vehicle type for anyone logged in: counts only, no names or plates. */
    @Transactional(readOnly = true)
    public List<DashboardResponse.TypeStatus> spaces() {
        return spaceStatuses(LocalDateTime.now(clock));
    }

    private List<DashboardResponse.TypeStatus> spaceStatuses(LocalDateTime now) {
        List<DashboardResponse.TypeStatus> types = new ArrayList<>();
        for (ParkingArea area : ParkingArea.values()) {
            for (VehicleType type : VehicleType.values()) {
                int capacity = capacities.findByAreaAndVehicleType(area, type).map(ParkingCapacity::getCapacity).orElse(0);
                long inside = logs.countByAreaAndVehicleTypeAndExitTimeIsNull(area, type);
                // An area with no spaces and nobody inside is not worth a row.
                if (capacity == 0 && inside == 0) {
                    continue;
                }
                long reservedNow = reservations
                        .findOverlapping(area, type, ReservationStatus.ACTIVE, now, now.plusMinutes(1)).size();
                types.add(new DashboardResponse.TypeStatus(area, type, capacity, inside,
                        Math.max(0L, capacity - inside), reservedNow));
            }
        }
        return types;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<DashboardResponse.TypeStatus> types = spaceStatuses(now);

        return new DashboardResponse(
                types,
                reservations.countByStatusAndStartTimeBetween(ReservationStatus.ACTIVE, dayStart, dayEnd),
                logs.countByEntryTimeBetween(dayStart, dayEnd),
                denied.countByAttemptedAtBetween(dayStart, dayEnd),
                vehicles.countByStatus(VehicleStatus.PENDING),
                reservations.countByStatus(ReservationStatus.PENDING),
                logs.countByExitTimeIsNullAndOverstayTrue(),
                deviceRegistry.list(),
                logService.recent(RECENT),
                logService.recentDenied(RECENT));
    }
}
