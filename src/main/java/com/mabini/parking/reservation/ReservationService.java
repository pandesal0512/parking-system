package com.mabini.parking.reservation;

import com.mabini.parking.capacity.AreaResolver;
import com.mabini.parking.capacity.ParkingCapacity;
import com.mabini.parking.capacity.ParkingCapacityRepository;
import com.mabini.parking.common.enums.ParkingArea;
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

    /** These count toward the "one per day" limit. Cancelled, rejected and expired ones do not, so people can retry. */
    private static final List<ReservationStatus> COUNTS_TOWARD_DAILY_LIMIT = List.of(
            ReservationStatus.PENDING, ReservationStatus.ACTIVE, ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW);

    private static final DateTimeFormatter FRIENDLY = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final ReservationRepository reservations;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final ParkingCapacityRepository capacities;
    private final AreaResolver areaResolver;
    private final AppProperties props;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, VehicleRepository vehicles, UserRepository users,
                              ParkingCapacityRepository capacities, AreaResolver areaResolver,
                              AppProperties props, Clock clock) {
        this.reservations = reservations;
        this.vehicles = vehicles;
        this.users = users;
        this.capacities = capacities;
        this.areaResolver = areaResolver;
        this.props = props;
        this.clock = clock;
    }

    /* ------------------------- availability ------------------------- */

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long userId, VehicleType type, LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now(clock);
        validateWindow(start, end, now);

        // The area depends on who is asking: faculty see the faculty area, everyone else the general lot.
        User asker = users.findById(userId).orElse(null);
        ParkingArea area = areaResolver.resolve(asker, type);

        int capacity = capacities.findByAreaAndVehicleType(area, type).map(ParkingCapacity::getCapacity).orElse(0);
        int peak = peakReserved(area, type, start, end);
        return new AvailabilityResponse(area, type, start, end, capacity, peak, Math.max(0, capacity - peak));
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

        ParkingArea area = areaResolver.resolve(user, vehicle.getVehicleType());

        // Take the lock on this area and vehicle type's capacity row. Anyone else booking the same type
        // now waits here until we commit, so the last space can't be booked twice.
        ParkingCapacity capacityRow = capacities.findForUpdate(area, vehicle.getVehicleType())
                .orElseThrow(() -> ApiException.conflict("NO_CAPACITY", "Parking is not available for this vehicle type."));

        if (reservations.countOverlappingForUser(userId, ReservationStatus.OPEN, start, end) > 0) {
            throw ApiException.conflict("ALREADY_RESERVED", "You already have a reservation that overlaps this time.");
        }

        // The limit is checked while we hold the lock, so two quick clicks can't both slip through.
        int maxPerDay = props.reservation().maxPerDay();
        LocalDateTime dayStart = start.toLocalDate().atStartOfDay();
        if (reservations.countForUserStartingBetween(userId, COUNTS_TOWARD_DAILY_LIMIT, dayStart, dayStart.plusDays(1))
                >= maxPerDay) {
            throw ApiException.conflict("DAILY_LIMIT",
                    maxPerDay == 1
                            ? "You can only reserve once per day. Cancel your other reservation for that day first."
                            : "You can only make " + maxPerDay + " reservations per day.");
        }

        int peak = peakReserved(area, vehicle.getVehicleType(), start, end);
        if (peak >= capacityRow.getCapacity()) {
            throw ApiException.conflict("FULL", "No space is left for this vehicle type in that time window.");
        }

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setVehicle(vehicle);
        reservation.setVehicleType(vehicle.getVehicleType());
        reservation.setArea(area);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        boolean auto = props.reservation().autoApprove();
        reservation.setStatus(auto ? ReservationStatus.ACTIVE : ReservationStatus.PENDING);
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
        if (reservation.getStatus() != ReservationStatus.ACTIVE && reservation.getStatus() != ReservationStatus.PENDING) {
            throw ApiException.conflict("NOT_ACTIVE", "Only waiting or approved reservations can be cancelled.");
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

    /** Every request still waiting for a decision, oldest start first. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> listPending() {
        return reservations.findByStatusOrderByStartTimeAsc(ReservationStatus.PENDING).stream()
                .map(r -> ReservationResponse.from(r, false))
                .toList();
    }

    /** Approving re-checks the space under the lock, because other requests may have been approved meanwhile. */
    @Transactional
    public ReservationResponse approve(Long reservationId, Long staffId) {
        Reservation reservation = findPending(reservationId);
        LocalDateTime now = LocalDateTime.now(clock);

        if (!reservation.getEndTime().isAfter(now)) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setReviewNote("Not reviewed before the reservation ended.");
            throw ApiException.conflict("ALREADY_ENDED", "This reservation's time has already passed.");
        }
        User owner = reservation.getUser();
        if (owner.isRegistrationBanned() || !owner.isActive()
                || (owner.getSuspendedUntil() != null && owner.getSuspendedUntil().isAfter(now))) {
            throw ApiException.conflict("USER_BLOCKED", "This user can't make reservations right now.");
        }
        if (reservation.getVehicle().getStatus() != VehicleStatus.APPROVED) {
            throw ApiException.conflict("VEHICLE_NOT_APPROVED", "The user's vehicle is not approved.");
        }

        ParkingCapacity row = capacities.findForUpdate(reservation.getArea(), reservation.getVehicleType())
                .orElseThrow(() -> ApiException.conflict("NO_CAPACITY", "Parking is not available for this vehicle type."));
        int peak = peakReserved(reservation.getArea(), reservation.getVehicleType(),
                reservation.getStartTime(), reservation.getEndTime());
        if (peak >= row.getCapacity()) {
            throw ApiException.conflict("FULL", "No space is left in that time window. Reject it or cancel another reservation first.");
        }

        reservation.setStatus(ReservationStatus.ACTIVE);
        markReviewed(reservation, staffId, null, now);
        return ReservationResponse.from(reservation, false);
    }

    @Transactional
    public ReservationResponse reject(Long reservationId, Long staffId, String note) {
        Reservation reservation = findPending(reservationId);
        reservation.setStatus(ReservationStatus.REJECTED);
        markReviewed(reservation, staffId, note, LocalDateTime.now(clock));
        return ReservationResponse.from(reservation, false);
    }

    private Reservation findPending(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw ApiException.conflict("NOT_PENDING", "This reservation is not waiting for a decision.");
        }
        return reservation;
    }

    private void markReviewed(Reservation reservation, Long staffId, String note, LocalDateTime now) {
        reservation.setReviewedBy(users.getReferenceById(staffId));
        reservation.setReviewedAt(now);
        reservation.setReviewNote(note == null || note.isBlank() ? null : note.trim());
    }

    /** Staff can cancel any waiting or approved reservation at any time. */
    @Transactional
    public ReservationResponse cancelAsStaff(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found."));
        if (reservation.getStatus() != ReservationStatus.ACTIVE && reservation.getStatus() != ReservationStatus.PENDING) {
            throw ApiException.conflict("NOT_ACTIVE", "Only waiting or approved reservations can be cancelled.");
        }
        cancel(reservation, LocalDateTime.now(clock));
        return ReservationResponse.from(reservation, false);
    }

    /* ------------------------- helpers ------------------------- */

    private int peakReserved(ParkingArea area, VehicleType type, LocalDateTime start, LocalDateTime end) {
        List<Concurrency.Window> windows = reservations
                .findOverlapping(area, type, ReservationStatus.ACTIVE, start, end).stream()
                .map(r -> new Concurrency.Window(r.getStartTime(), r.getEndTime()))
                .toList();
        return Concurrency.peak(windows, start, end);
    }

    private void cancel(Reservation reservation, LocalDateTime now) {
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
    }

    private boolean isCancellable(Reservation r, LocalDateTime now) {
        if (r.getStatus() == ReservationStatus.PENDING) {
            return true; // a request nobody has approved yet can always be withdrawn
        }
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
