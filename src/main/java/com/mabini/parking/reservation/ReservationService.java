package com.mabini.parking.reservation;

import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.config.AppProperties;
import com.mabini.parking.reservation.dto.AvailabilityResponse;
import com.mabini.parking.reservation.dto.CreateReservationRequest;
import com.mabini.parking.reservation.dto.ReservationResponse;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import com.mabini.parking.vehicle.Vehicle;
import com.mabini.parking.vehicle.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReservationService {

    private static final DateTimeFormatter FRIENDLY = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final ReservationRepository reservations;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final ParkingCapacityRepository capacities;
    private final AppProperties props;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, VehicleRepository vehicles, UserRepository users,
                              ParkingCapacityRepository capacities, AppProperties props, Clock clock) {
        this.reservations = reservations;
        this.vehicles = vehicles;
        this.users = users;
        this.capacities = capacities;
        this.props = props;
        this.clock = clock;
    }

    /* ------------------------- availability ------------------------- */

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(VehicleType type, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now(clock);
        validateWindow(start, end, now);

        int capacity = capacities.findById(type).map(ParkingCapacity::getCapacity).orElse(0);
        int peak = peakReserved(type, start, end);
        return new AvailabilityResponse(type, start, end, capacity, peak, Math.max(0, capacity - peak));
    }

    /* ------------------------- user actions ------------------------- */

    @Transactional
    public ReservationResponse create(Long userId, CreateReservationRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = request.start();
        LocalDateTime end = request.end();

        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication is required."));

        if (user.isRegistrationBanned()) {
            throw ApiException.forbidden("REGISTRATION_BANNED",
                    "You are no longer allowed to make parking reservations. Contact the parking office.");
        }
        if (user.getSuspendedUntil() != null && user.getSuspendedUntil().isAfter(now)) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED",
                    "Your reservations are suspended until " + user.getSuspendedUntil().format(FRIENDLY) + ".");
        }

        Vehicle vehicle = vehicles.findByOwnerId(userId)
                .orElseThrow(() -> ApiException.notFound("NO_VEHICLE", "Register your vehicle before reserving parking."));
        if (vehicle.getStatus() != VehicleStatus.APPROVED) {
            throw ApiException.conflict("VEHICLE_NOT_APPROVED",
                    "Your vehicle must be approved before you can reserve parking.");
        }

        validateWindow(start, end, now);

        // Take the lock on this vehicle type's capacity row. Anyone else booking the same type
        // now waits here until we commit, so the last space can't be booked twice.
        ParkingCapacity capacityRow = capacities.findForUpdate(vehicle.getVehicleType())
                .orElseThrow(() -> ApiException.conflict("NO_CAPACITY", "Parking is not available for this vehicle type."));

        if (reservations.countOverlappingForUser(userId, ReservationStatus.ACTIVE, start, end) > 0) {
            throw ApiException.conflict("ALREADY_RESERVED", "You already have a reservation that overlaps this time.");
        }

        int peak = peakReserved(vehicle.getVehicleType(), start, end);
        if (peak >= capacityRow.getCapacity()) {
            throw ApiException.conflict("FULL", "No space is left for this vehicle type in that time window.");
        }

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setVehicle(vehicle);
        reservation.setVehicleType(vehicle.getVehicleType());
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setCreatedAt(now);
        Reservation saved = reservations.save(reservation);
        return ReservationResponse.from(saved, isCancellable(saved, now));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listMine(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return reservations.findByUserIdOrderByStartTimeDesc(userId).stream()
                .map(r -> ReservationResponse.from(r, isCancellable(r, now)))
                .toList();
    }

    /**
     * A user can cancel only their OWN reservation, and only until the cutoff before it starts.
     * Someone else's reservation answers "not found", so ids can't be probed.
     */
    @Transactional
    public ReservationResponse cancelMine(Long userId, Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .filter(r -> r.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found."));

        LocalDateTime now = LocalDateTime.now(clock);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw ApiException.conflict("NOT_ACTIVE", "Only active reservations can be cancelled.");
        }
        if (!isCancellable(reservation, now)) {
            throw ApiException.conflict("CANCELLATION_TOO_LATE",
                    "Reservations can only be cancelled up to " + props.reservation().cancelCutoffMinutes()
                            + " minutes before they start.");
        }
        cancel(reservation, now);
        return ReservationResponse.from(reservation, false);
    }

    /* ------------------------- manager actions ------------------------- */

    /** Reservations that touch the given day, optionally filtered by status. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> listForDay(LocalDate day, ReservationStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate chosen = (day != null) ? day : now.toLocalDate();
        LocalDateTime from = chosen.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        List<Reservation> found = (status == null)
                ? reservations.findInWindow(from, to)
                : reservations.findInWindowByStatus(from, to, status);
        return found.stream().map(r -> ReservationResponse.from(r, isCancellable(r, now))).toList();
    }

    /** Staff can cancel any active reservation at any time. */
    @Transactional
    public ReservationResponse cancelAsStaff(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw ApiException.conflict("NOT_ACTIVE", "Only active reservations can be cancelled.");
        }
        cancel(reservation, LocalDateTime.now(clock));
        return ReservationResponse.from(reservation, false);
    }

    /* ------------------------- helpers ------------------------- */

    private int peakReserved(VehicleType type, LocalDateTime start, LocalDateTime end) {
        List<Concurrency.Window> windows = reservations
                .findOverlapping(type, ReservationStatus.ACTIVE, start, end).stream()
                .map(r -> new Concurrency.Window(r.getStartTime(), r.getEndTime()))
                .toList();
        return Concurrency.peak(windows, start, end);
    }

    private void cancel(Reservation reservation, LocalDateTime now) {
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
    }

    private boolean isCancellable(Reservation r, LocalDateTime now) {
        return r.getStatus() == ReservationStatus.ACTIVE
                && !now.isAfter(r.getStartTime().minusMinutes(props.reservation().cancelCutoffMinutes()));
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        AppProperties.ReservationRules rules = props.reservation();

        // Minute precision: a booking for "right now" typed into a form is not in the past.
        if (start.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) {
            throw ApiException.badRequest("START_IN_PAST", "The start time cannot be in the past.");
        }
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_WINDOW", "The end time must be after the start time.");
        }
        if (Duration.between(start, end).toMinutes() > rules.maxHours() * 60L) {
            throw ApiException.badRequest("TOO_LONG",
                    "A reservation can be at most " + rules.maxHours() + " hours long.");
        }
        if (start.isAfter(now.plusDays(rules.maxDaysAhead()))) {
            throw ApiException.badRequest("TOO_FAR_AHEAD",
                    "You can reserve at most " + rules.maxDaysAhead() + " days ahead.");
        }
    }
}
