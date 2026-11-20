package com.mabini.parking.penalty;

import com.mabini.parking.common.enums.PenaltyType;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.config.AppProperties;
import com.mabini.parking.log.AccessLog;
import com.mabini.parking.log.AccessLogRepository;
import com.mabini.parking.penalty.dto.CheckResult;
import com.mabini.parking.penalty.dto.PenaltyResponse;
import com.mabini.parking.reservation.Reservation;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The penalty rules.
 *
 *  - NO-SHOW: a reservation ended and the vehicle never entered. The reservation becomes NO_SHOW and
 *    the user gets a warning. At app.penalty.no-show-ban-threshold warnings the user can no longer
 *    reserve parking until a manager clears it.
 *  - OVERSTAY: the vehicle is still inside (or left late) after the reservation end plus the grace
 *    period. The user cannot reserve or enter for app.penalty.overstay-suspension-days.
 *
 * When a ban or suspension starts, the user's OTHER active reservations are cancelled, so those spaces
 * go back to everyone else.
 *
 * Each reservation can be punished once per type (also enforced by a database constraint), so running
 * the check often, or the gate and the background check both noticing the same overstay, is safe.
 */
@Service
public class PenaltyService {

    private static final DateTimeFormatter FRIENDLY = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final UserRepository users;
    private final PenaltyRepository penalties;
    private final ReservationRepository reservations;
    private final AccessLogRepository logs;
    private final AppProperties props;
    private final Clock clock;

    public PenaltyService(UserRepository users, PenaltyRepository penalties, ReservationRepository reservations,
                          AccessLogRepository logs, AppProperties props, Clock clock) {
        this.users = users;
        this.penalties = penalties;
        this.reservations = reservations;
        this.logs = logs;
        this.props = props;
        this.clock = clock;
    }

    /** The background check (also callable by hand from the demo page). */
    @Transactional
    public CheckResult runChecks() {
        LocalDateTime now = LocalDateTime.now(clock);

        // Requests nobody reviewed before their time window ended can no longer be approved.
        for (Reservation r : reservations.findByStatusAndEndTimeLessThanEqual(ReservationStatus.PENDING, now)) {
            r.setStatus(ReservationStatus.EXPIRED);
            r.setReviewNote("Not reviewed before the reservation ended.");
            reservations.save(r);
        }

        int noShows = 0;
        for (Reservation r : reservations.findUnusedEnded(ReservationStatus.ACTIVE, now)) {
            r.setStatus(ReservationStatus.NO_SHOW);
            reservations.save(r);
            applyNoShow(r, now);
            noShows++;
        }

        int overstays = 0;
        LocalDateTime cutoff = now.minusMinutes(props.reservation().graceMinutes());
        for (AccessLog entry : logs.findOverstaying(cutoff)) {
            entry.setOverstay(true);
            logs.save(entry);
            applyOverstay(entry.getReservation(), now);
            overstays++;
        }
        return new CheckResult(noShows, overstays);
    }

    /** Called by the gate when a vehicle leaves late. Safe to call twice for the same reservation. */
    @Transactional
    public void applyOverstay(Reservation reservation, LocalDateTime now) {
        if (reservation == null || penalties.existsByReservationIdAndType(reservation.getId(), PenaltyType.OVERSTAY)) {
            return;
        }
        User user = reservation.getUser();
        LocalDateTime until = now.plusDays(props.penalty().overstaySuspensionDays());
        if (user.getSuspendedUntil() == null || user.getSuspendedUntil().isBefore(until)) {
            user.setSuspendedUntil(until);
        }
        users.save(user);
        cancelOtherActiveReservations(user, reservation.getId(), now);

        record(user, reservation, PenaltyType.OVERSTAY, now,
                "Stayed past the reservation that ended " + reservation.getEndTime().format(FRIENDLY)
                        + ". Cannot reserve or enter until " + user.getSuspendedUntil().format(FRIENDLY) + ".");
    }

    private void applyNoShow(Reservation reservation, LocalDateTime now) {
        if (penalties.existsByReservationIdAndType(reservation.getId(), PenaltyType.NO_SHOW)) {
            return;
        }
        User user = reservation.getUser();
        int warnings = user.getWarningCount() + 1;
        user.setWarningCount(warnings);

        String text = "Did not arrive for the reservation on " + reservation.getStartTime().format(FRIENDLY)
                + ". Warning " + warnings + ".";
        int threshold = props.penalty().noShowBanThreshold();
        if (warnings >= threshold && !user.isRegistrationBanned()) {
            user.setRegistrationBanned(true);
            text += " Reservations are now disabled after " + threshold + " no-shows.";
        }
        users.save(user);
        if (user.isRegistrationBanned()) {
            cancelOtherActiveReservations(user, reservation.getId(), now);
        }
        record(user, reservation, PenaltyType.NO_SHOW, now, text);
    }

    /** Frees the spaces held by the user's other active reservations (never the one being punished). */
    private void cancelOtherActiveReservations(User user, Long exceptReservationId, LocalDateTime now) {
        for (Reservation other : reservations.findByUserIdAndStatusIn(user.getId(), ReservationStatus.OPEN)) {
            if (!other.getId().equals(exceptReservationId)) {
                other.setStatus(ReservationStatus.CANCELLED);
                other.setCancelledAt(now);
                reservations.save(other);
            }
        }
    }

    private void record(User user, Reservation reservation, PenaltyType type, LocalDateTime now, String text) {
        Penalty penalty = new Penalty();
        penalty.setUser(user);
        penalty.setReservation(reservation);
        penalty.setType(type);
        penalty.setDescription(text.length() > 255 ? text.substring(0, 255) : text);
        penalty.setCreatedAt(now);
        penalties.save(penalty);
    }

    /* ------------------------- reading ------------------------- */

    @Transactional(readOnly = true)
    public List<PenaltyResponse> listMine(Long userId) {
        return penalties.findByUserIdOrderByCreatedAtDesc(userId).stream().map(PenaltyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PenaltyResponse> listAll(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return penalties.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(PenaltyResponse::from).toList();
    }

    /* ------------------------- manager action ------------------------- */

    /** Appeals and mistakes: removes warnings, the suspension and the ban. The history stays. */
    @Transactional
    public void clearPenalties(Long userId) {
        User user = users.findById(userId)
                .filter(u -> u.getRole() == com.mabini.parking.common.enums.Role.USER)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));
        user.setWarningCount(0);
        user.setSuspendedUntil(null);
        user.setRegistrationBanned(false);
        users.save(user);
    }
}
