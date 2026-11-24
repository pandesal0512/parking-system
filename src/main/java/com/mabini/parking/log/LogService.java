package com.mabini.parking.log;

import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.log.dto.DeniedResponse;
import com.mabini.parking.log.dto.LogResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class LogService {

    private static final int MAX_LIMIT = 200;

    private final AccessLogRepository logs;
    private static final int MAX_EXPORT_DAYS = 366;
    private static final DateTimeFormatter CELL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DeniedAttemptRepository denied;
    private final Clock clock;

    public LogService(AccessLogRepository logs, DeniedAttemptRepository denied, Clock clock) {
        this.logs = logs;
        this.denied = denied;
        this.clock = clock;
    }

    /**
     * Every visit (vehicle that parked) that started between the two dates, both included, as a CSV file.
     * Reservation details are filled in when the visit was linked to a reservation.
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(30);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_RANGE", "The end date must not be before the start date.");
        }
        if (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > MAX_EXPORT_DAYS) {
            throw ApiException.badRequest("RANGE_TOO_LONG", "Choose a range of at most one year.");
        }

        CsvWriter csv = new CsvWriter().row(List.of(
                "Entered", "Left", "Minutes parked", "Plate number", "Name", "Who", "Vehicle type", "Parking area",
                "Reservation start", "Reservation end", "Visit reason", "Manual override", "Override reason", "Overstay"));

        for (AccessLog log : logs.findForExport(start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
            var vehicle = log.getVehicle();
            boolean visitor = vehicle == null;
            var reservation = log.getReservation();
            csv.row(List.of(
                    CELL_TIME.format(log.getEntryTime()),
                    log.getExitTime() == null ? "Still inside" : CELL_TIME.format(log.getExitTime()),
                    log.getExitTime() == null ? "" : String.valueOf(Duration.between(log.getEntryTime(), log.getExitTime()).toMinutes()),
                    nullToEmpty(log.getIdentifierRead()),
                    visitor ? nullToEmpty(log.getVisitorName()) : vehicle.getOwner().getFullName(),
                    visitor ? "Visitor" : nice(vehicle.getOwner().getUserType()),
                    nice(log.getVehicleType()),
                    nice(log.getArea()),
                    reservation == null ? "" : CELL_TIME.format(reservation.getStartTime()),
                    reservation == null ? "" : CELL_TIME.format(reservation.getEndTime()),
                    nullToEmpty(log.getVisitReason()),
                    log.isOverride() ? "Yes" : "No",
                    nullToEmpty(log.getOverrideReason()),
                    log.isOverstay() ? "Yes" : "No"));
        }
        return csv.toBytes();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** FACULTY_MEMBER style names to "Faculty member". */
    private static String nice(Enum<?> value) {
        if (value == null) return "";
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** A user's own log book: only visits of their own vehicle. */
    @Transactional(readOnly = true)
    public List<LogResponse> mine(Long userId, int limit) {
        return logs.findByOwner(userId, PageRequest.of(0, clamp(limit))).stream()
                .map(LogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogResponse> recent(int limit) {
        return logs.findAllByOrderByEntryTimeDesc(PageRequest.of(0, clamp(limit))).stream()
                .map(LogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeniedResponse> recentDenied(int limit) {
        return denied.findAllByOrderByAttemptedAtDesc(PageRequest.of(0, clamp(limit))).stream()
                .map(DeniedResponse::from)
                .toList();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
