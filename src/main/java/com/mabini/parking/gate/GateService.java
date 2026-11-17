package com.mabini.parking.gate;

import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.Identifiers;
import com.mabini.parking.common.enums.DenyReason;
import com.mabini.parking.common.enums.Direction;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.config.AppProperties;
import com.mabini.parking.gate.dto.GateRequest;
import com.mabini.parking.gate.dto.GateResult;
import com.mabini.parking.gate.dto.VisitorRequest;
import com.mabini.parking.log.AccessLog;
import com.mabini.parking.log.AccessLogRepository;
import com.mabini.parking.log.DeniedAttempt;
import com.mabini.parking.log.DeniedAttemptRepository;
import com.mabini.parking.log.dto.LogResponse;
import com.mabini.parking.reservation.Reservation;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.user.User;
import com.mabini.parking.vehicle.Vehicle;
import com.mabini.parking.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The gate decision. Today a staff member triggers it from the gate simulator; a camera device
 * can call the same logic later.
 *
 * Entry checks, in order: registered, approved, owner active, owner not suspended, not already
 * inside, has a reservation that is valid right now. Every refusal is recorded.
 */
@Service
public class GateService {

    private static final DateTimeFormatter FRIENDLY = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final VehicleRepository vehicles;
    private final AccessLogRepository logs;
    private final DeniedAttemptRepository denied;
    private final ReservationRepository reservations;
    private final ParkingCapacityRepository capacities;
    private final AppProperties props;
    private final Clock clock;

    public GateService(VehicleRepository vehicles, AccessLogRepository logs, DeniedAttemptRepository denied,
                       ReservationRepository reservations, ParkingCapacityRepository capacities,
                       AppProperties props, Clock clock) {
        this.vehicles = vehicles;
        this.logs = logs;
        this.denied = denied;
        this.reservations = reservations;
        this.capacities = capacities;
        this.props = props;
        this.clock = clock;
    }

    /* ------------------------- members: plate in, plate out ------------------------- */

    @Transactional
    public GateResult verify(Long staffId, GateRequest request) {
        String identifier = Identifiers.normalize(request.identifier());
        if (identifier.isEmpty()) {
            throw ApiException.badRequest("INVALID_IDENTIFIER", "Enter a plate number.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return request.direction() == Direction.IN
                ? enter(staffId, identifier, now)
                : leave(identifier, now);
    }

    private GateResult enter(Long staffId, String identifier, LocalDateTime now) {
        Vehicle vehicle = vehicles.findByIdentifier(identifier).orElse(null);
        if (vehicle == null) {
            return deny(staffId, identifier, null, null, DenyReason.UNREGISTERED,
                    "This plate is not registered.", now);
        }

        User owner = vehicle.getOwner();
        VehicleType type = vehicle.getVehicleType();
        String ownerName = owner.getFullName();

        if (vehicle.getStatus() != VehicleStatus.APPROVED) {
            return deny(staffId, identifier, ownerName, type, DenyReason.VEHICLE_NOT_APPROVED,
                    "This vehicle is not approved.", now);
        }
        if (!owner.isActive()) {
            return deny(staffId, identifier, ownerName, type, DenyReason.ACCOUNT_DISABLED,
                    "The owner's account is deactivated.", now);
        }
        if (owner.getSuspendedUntil() != null && owner.getSuspendedUntil().isAfter(now)) {
            return deny(staffId, identifier, ownerName, type, DenyReason.ACCOUNT_SUSPENDED,
                    "The owner is suspended from parking until " + owner.getSuspendedUntil().format(FRIENDLY) + ".", now);
        }
        if (logs.existsByVehicleIdAndExitTimeIsNull(vehicle.getId())) {
            return deny(staffId, identifier, ownerName, type, DenyReason.ALREADY_INSIDE,
                    "This vehicle is already recorded inside.", now);
        }

        int grace = props.reservation().graceMinutes();
        List<Reservation> usable = reservations.findUsable(
                vehicle.getId(), ReservationStatus.ACTIVE, now.plusMinutes(grace), now);
        if (usable.isEmpty()) {
            return deny(staffId, identifier, ownerName, type, DenyReason.NO_RESERVATION,
                    "No valid reservation for this time.", now);
        }
        Reservation reservation = usable.get(0);

        AccessLog entry = new AccessLog();
        entry.setVehicle(vehicle);
        entry.setIdentifierRead(identifier);
        entry.setVehicleType(type);
        entry.setReservation(reservation);
        entry.setEntryTime(now);
        entry.setRecordedBy(staffId);
        logs.save(entry);

        return new GateResult(true, null,
                "Entry recorded. The reservation ends " + reservation.getEndTime().format(FRIENDLY) + ".",
                Direction.IN, identifier, ownerName, type);
    }

    private GateResult leave(String identifier, LocalDateTime now) {
        AccessLog open = logs.findFirstByIdentifierReadAndExitTimeIsNullOrderByEntryTimeDesc(identifier).orElse(null);
        if (open == null) {
            // Not an error: the vehicle may have been let in by hand and never recorded.
            return new GateResult(true, "NO_ENTRY_RECORD",
                    "No entry record for this plate, so there is nothing to close.",
                    Direction.OUT, identifier, null, null);
        }

        boolean overstay = closeLog(open, now);
        return new GateResult(true, overstay ? "OVERSTAY" : null,
                overstay ? "Exit recorded. The vehicle stayed past its reservation." : "Exit recorded.",
                Direction.OUT, identifier, nameOf(open), open.getVehicleType());
    }

    /* ------------------------- visitors ------------------------- */

    @Transactional
    public GateResult visitorEntry(Long staffId, VisitorRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        String name = request.name().trim();
        VehicleType type = request.vehicleType();

        String identifier = null;
        if (request.identifier() != null && !request.identifier().isBlank()) {
            identifier = Identifiers.normalize(request.identifier());
            if (!Identifiers.isValid(identifier)) {
                throw ApiException.badRequest("INVALID_IDENTIFIER",
                        "Enter the plate number using 2 to 12 letters and numbers, or leave it empty.");
            }
            if (vehicles.existsByIdentifier(identifier)) {
                throw ApiException.badRequest("REGISTERED_PLATE",
                        "This plate belongs to a registered vehicle. Use the plate entry instead.");
            }
        }

        // Lock this vehicle type's capacity row so two visitors can't take the last space together.
        ParkingCapacity capacity = capacities.findForUpdate(type)
                .orElseThrow(() -> ApiException.conflict("NO_CAPACITY", "Parking is not available for this vehicle type."));

        if (identifier != null
                && logs.findFirstByIdentifierReadAndExitTimeIsNullOrderByEntryTimeDesc(identifier).isPresent()) {
            return deny(staffId, identifier, name, type, DenyReason.ALREADY_INSIDE,
                    "A vehicle with this plate is already recorded inside.", now);
        }
        if (logs.countByVehicleTypeAndExitTimeIsNull(type) >= capacity.getCapacity()) {
            return deny(staffId, identifier, name, type, DenyReason.LOT_FULL,
                    "No space is left for this vehicle type.", now);
        }

        AccessLog entry = new AccessLog();
        entry.setIdentifierRead(identifier);
        entry.setVisitorName(name);
        entry.setVehicleType(type);
        entry.setEntryTime(now);
        entry.setRecordedBy(staffId);
        logs.save(entry);

        return new GateResult(true, null, "Visitor recorded.", Direction.IN, identifier, name, type);
    }

    /* ------------------------- who is inside / manual exit ------------------------- */

    @Transactional(readOnly = true)
    public List<LogResponse> inside() {
        return logs.findByExitTimeIsNullOrderByEntryTimeDesc().stream().map(LogResponse::from).toList();
    }

    /** Closes any open visit by its log id. This is how a visitor without a plate leaves. */
    @Transactional
    public LogResponse exitByLogId(Long logId) {
        AccessLog entry = logs.findById(logId)
                .orElseThrow(() -> ApiException.notFound("LOG_NOT_FOUND", "That visit was not found."));
        if (entry.getExitTime() != null) {
            throw ApiException.conflict("ALREADY_EXITED", "This vehicle is already recorded as exited.");
        }
        closeLog(entry, LocalDateTime.now(clock));
        return LogResponse.from(entry);
    }

    /* ------------------------- helpers ------------------------- */

    /** Sets the exit time, completes the reservation, and returns true if it was an overstay. */
    private boolean closeLog(AccessLog entry, LocalDateTime now) {
        entry.setExitTime(now);
        boolean overstay = false;
        Reservation reservation = entry.getReservation();
        if (reservation != null) {
            overstay = now.isAfter(reservation.getEndTime().plusMinutes(props.reservation().graceMinutes()));
            reservation.setStatus(ReservationStatus.COMPLETED);
        }
        entry.setOverstay(overstay);
        return overstay;
    }

    private static String nameOf(AccessLog entry) {
        Vehicle vehicle = entry.getVehicle();
        return vehicle != null ? vehicle.getOwner().getFullName() : entry.getVisitorName();
    }

    /** Records the refusal and builds the answer. */
    private GateResult deny(Long staffId, String identifier, String ownerName, VehicleType type,
                            DenyReason reason, String message, LocalDateTime now) {
        DeniedAttempt attempt = new DeniedAttempt();
        attempt.setIdentifierRead(identifier);
        attempt.setReason(reason);
        attempt.setAttemptedAt(now);
        attempt.setRecordedBy(staffId);
        denied.save(attempt);
        return new GateResult(false, reason.name(), message, Direction.IN, identifier, ownerName, type);
    }
}
