package com.mabini.parking.reservation;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.common.Roles;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.reservation.dto.AvailabilityResponse;
import com.mabini.parking.reservation.dto.CreateReservationRequest;
import com.mabini.parking.reservation.dto.RejectRequest;
import com.mabini.parking.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Any logged-in person can check how many spaces are left. Example: ?vehicleType=CAR&start=2026-11-20T08:00&end=2026-11-20T10:00 */
    @GetMapping("/reservations/availability")
    public AvailabilityResponse availability(
            @AuthenticationPrincipal AuthUser current,
            @RequestParam VehicleType vehicleType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return reservationService.availability(current.id(), vehicleType, start, end);
    }

    /* ----- a user's own reservations ----- */

    @PostMapping("/reservations")
    @PreAuthorize(Roles.USER_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@AuthenticationPrincipal AuthUser current,
                                      @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(current.id(), request);
    }

    @GetMapping("/reservations/me")
    @PreAuthorize(Roles.USER_ONLY)
    public List<ReservationResponse> mine(@AuthenticationPrincipal AuthUser current) {
        return reservationService.listMine(current.id());
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize(Roles.USER_ONLY)
    public ReservationResponse cancel(@AuthenticationPrincipal AuthUser current, @PathVariable Long id) {
        return reservationService.cancelMine(current.id(), id);
    }

    /* ----- parking manager / super admin ----- */

    /** Reservations touching a day (default: today), optionally filtered by status. */
    @GetMapping("/manager/reservations")
    @PreAuthorize(Roles.STAFF)
    public List<ReservationResponse> listForDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ReservationStatus status) {
        return reservationService.listForDay(date, status);
    }

    /** Requests waiting for approval, whatever day they are for. */
    @GetMapping("/manager/reservations/pending")
    @PreAuthorize(Roles.STAFF)
    public List<ReservationResponse> pending() {
        return reservationService.listPending();
    }

    @PostMapping("/manager/reservations/{id}/approve")
    @PreAuthorize(Roles.STAFF)
    public ReservationResponse approve(@AuthenticationPrincipal AuthUser current, @PathVariable Long id) {
        return reservationService.approve(id, current.id());
    }

    @PostMapping("/manager/reservations/{id}/reject")
    @PreAuthorize(Roles.STAFF)
    public ReservationResponse reject(@AuthenticationPrincipal AuthUser current, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) RejectRequest request) {
        return reservationService.reject(id, current.id(), request == null ? null : request.note());
    }

    @PostMapping("/manager/reservations/{id}/cancel")
    @PreAuthorize(Roles.STAFF)
    public ReservationResponse cancelAsStaff(@PathVariable Long id) {
        return reservationService.cancelAsStaff(id);
    }
}
