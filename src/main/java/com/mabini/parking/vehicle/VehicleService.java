package com.mabini.parking.vehicle;

import com.mabini.parking.common.Identifiers;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.config.AppProperties;
import com.mabini.parking.log.AccessLogRepository;
import com.mabini.parking.reservation.Reservation;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import com.mabini.parking.vehicle.dto.VehicleRequest;
import com.mabini.parking.vehicle.dto.VehicleResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final AccessLogRepository logs;
    private final AppProperties props;
    private final Clock clock;

    public VehicleService(VehicleRepository vehicles, UserRepository users, ReservationRepository reservations,
                          AccessLogRepository logs, AppProperties props, Clock clock) {
        this.vehicles = vehicles;
        this.users = users;
        this.reservations = reservations;
        this.logs = logs;
        this.props = props;
        this.clock = clock;
    }

    /* ------------------------- the user's own vehicle ------------------------- */

    @Transactional(readOnly = true)
    public VehicleResponse getMine(Long userId) {
        Vehicle vehicle = vehicles.findByOwnerId(userId).orElseThrow(VehicleService::noVehicle);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse register(Long userId, VehicleRequest request) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required."));

        if (user.isRegistrationBanned()) {
            throw ApiException.forbidden("REGISTRATION_BANNED",
                    "You are no longer allowed to register a vehicle. Contact the parking office.");
        }
        if (vehicles.findByOwnerId(userId).isPresent()) {
            throw ApiException.conflict("VEHICLE_ALREADY_REGISTERED",
                    "You already have a registered vehicle. Edit it instead.");
        }

        String identifier = cleanIdentifier(request.identifier());
        if (vehicles.existsByIdentifier(identifier)) {
            throw ApiException.conflict("IDENTIFIER_TAKEN", "That plate number is already registered.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(user);
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setIdentifier(identifier);
        vehicle.setMake(blankToNull(request.make()));
        vehicle.setModel(blankToNull(request.model()));
        vehicle.setColor(blankToNull(request.color()));
        vehicle.setCreatedAt(now);
        resetReview(vehicle, now);

        try {
            return VehicleResponse.from(vehicles.saveAndFlush(vehicle));
        } catch (DataIntegrityViolationException e) {
            // Someone registered the same plate at the same moment.
            throw ApiException.conflict("IDENTIFIER_TAKEN", "That plate number is already registered.");
        }
    }

    @Transactional
    public VehicleResponse updateMine(Long userId, VehicleRequest request) {
        Vehicle vehicle = vehicles.findByOwnerId(userId).orElseThrow(VehicleService::noVehicle);

        if (vehicle.getStatus() == VehicleStatus.REVOKED) {
            throw ApiException.forbidden("VEHICLE_REVOKED",
                    "This vehicle was revoked by the parking office. Contact them to restore it.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String identifier = cleanIdentifier(request.identifier());
        boolean identityChanged = !identifier.equals(vehicle.getIdentifier())
                || request.vehicleType() != vehicle.getVehicleType();

        if (identityChanged) {
            // A new plate or type has to be approved again, and must not break existing bookings.
            if (reservations.existsByVehicleIdAndStatus(vehicle.getId(), ReservationStatus.ACTIVE)) {
                throw ApiException.conflict("HAS_ACTIVE_RESERVATION",
                        "Cancel your active reservations before changing the plate number or vehicle type.");
            }
            if (logs.existsByVehicleIdAndExitTimeIsNull(vehicle.getId())) {
                throw ApiException.conflict("VEHICLE_INSIDE",
                        "This vehicle is inside the parking lot. Change it after it leaves.");
            }
            if (!identifier.equals(vehicle.getIdentifier()) && vehicles.existsByIdentifier(identifier)) {
                throw ApiException.conflict("IDENTIFIER_TAKEN", "That plate number is already registered.");
            }
            vehicle.setIdentifier(identifier);
            vehicle.setVehicleType(request.vehicleType());
        }

        vehicle.setMake(blankToNull(request.make()));
        vehicle.setModel(blankToNull(request.model()));
        vehicle.setColor(blankToNull(request.color()));

        // New identity, or saving a rejected vehicle again, means "please review this again".
        if (identityChanged || vehicle.getStatus() == VehicleStatus.REJECTED) {
            resetReview(vehicle, now);
        }

        try {
            return VehicleResponse.from(vehicles.saveAndFlush(vehicle));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("IDENTIFIER_TAKEN", "That plate number is already registered.");
        }
    }

    /* ------------------------- manager actions ------------------------- */

    @Transactional(readOnly = true)
    public List<VehicleResponse> list(VehicleStatus status) {
        List<Vehicle> found = (status == null)
                ? vehicles.findAllByOrderByCreatedAtDesc()
                : vehicles.findByStatusOrderByCreatedAtDesc(status);
        return found.stream().map(VehicleResponse::from).toList();
    }

    @Transactional
    public VehicleResponse approve(Long vehicleId, Long staffId) {
        Vehicle vehicle = find(vehicleId);
        requireStatus(vehicle, VehicleStatus.PENDING, VehicleStatus.REJECTED, VehicleStatus.REVOKED);
        review(vehicle, VehicleStatus.APPROVED, staffId);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse reject(Long vehicleId, Long staffId) {
        Vehicle vehicle = find(vehicleId);
        requireStatus(vehicle, VehicleStatus.PENDING);
        review(vehicle, VehicleStatus.REJECTED, staffId);
        return VehicleResponse.from(vehicle);
    }

    /** Revoking an approved vehicle also cancels its active reservations. */
    @Transactional
    public VehicleResponse revoke(Long vehicleId, Long staffId) {
        Vehicle vehicle = find(vehicleId);
        requireStatus(vehicle, VehicleStatus.APPROVED);
        review(vehicle, VehicleStatus.REVOKED, staffId);

        LocalDateTime now = LocalDateTime.now(clock);
        for (Reservation r : reservations.findByVehicleIdAndStatus(vehicle.getId(), ReservationStatus.ACTIVE)) {
            r.setStatus(ReservationStatus.CANCELLED);
            r.setCancelledAt(now);
        }
        return VehicleResponse.from(vehicle);
    }

    /* ------------------------- helpers ------------------------- */

    private Vehicle find(Long id) {
        return vehicles.findById(id)
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Vehicle not found."));
    }

    private void requireStatus(Vehicle vehicle, VehicleStatus... allowed) {
        for (VehicleStatus status : allowed) {
            if (vehicle.getStatus() == status) {
                return;
            }
        }
        throw ApiException.conflict("INVALID_STATUS",
                "This vehicle is " + vehicle.getStatus().name().toLowerCase() + " and cannot be changed that way.");
    }

    private void review(Vehicle vehicle, VehicleStatus newStatus, Long staffId) {
        vehicle.setStatus(newStatus);
        vehicle.setReviewedBy(users.getReferenceById(staffId));
        vehicle.setReviewedAt(LocalDateTime.now(clock));
    }

    /** Puts the vehicle back to "needs review", or approves it straight away if auto-approve is on. */
    private void resetReview(Vehicle vehicle, LocalDateTime now) {
        vehicle.setReviewedBy(null);
        if (props.vehicle().autoApprove()) {
            vehicle.setStatus(VehicleStatus.APPROVED);
            vehicle.setReviewedAt(now);
        } else {
            vehicle.setStatus(VehicleStatus.PENDING);
            vehicle.setReviewedAt(null);
        }
    }

    private static String cleanIdentifier(String raw) {
        String normalized = Identifiers.normalize(raw);
        if (!Identifiers.isValid(normalized)) {
            throw ApiException.badRequest("INVALID_IDENTIFIER",
                    "Enter the plate number using 2 to 12 letters and numbers, for example ABC1234.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static ApiException noVehicle() {
        return ApiException.notFound("NO_VEHICLE", "You have not registered a vehicle yet.");
    }
}
