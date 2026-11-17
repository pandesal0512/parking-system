package com.mabini.parking.reservation;

import com.mabini.parking.reservation.Concurrency.Window;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plain unit test (no Spring, no database) for the "busiest moment" calculation. */
class ConcurrencyTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 20, hour, minute);
    }

    private static Window w(int fromHour, int toHour) {
        return new Window(at(fromHour, 0), at(toHour, 0));
    }

    @Test
    void noReservationsMeansPeakZero() {
        assertEquals(0, Concurrency.peak(List.of(), at(8, 0), at(12, 0)));
    }

    @Test
    void reservationsThatDoNotOverlapEachOtherCountOnce() {
        // 8-9 and 11-12 never happen at the same time, so the peak is 1, not 2.
        assertEquals(1, Concurrency.peak(List.of(w(8, 9), w(11, 12)), at(8, 0), at(12, 0)));
    }

    @Test
    void reservationsAtTheSameTimeStack() {
        assertEquals(3, Concurrency.peak(List.of(w(8, 10), w(9, 11), w(9, 10)), at(8, 0), at(12, 0)));
    }

    @Test
    void backToBackReservationsDoNotOverlap() {
        // One ends at 9:00 as the next starts at 9:00. The space is reused, so the peak is 1.
        assertEquals(1, Concurrency.peak(List.of(w(8, 9), w(9, 10)), at(8, 0), at(10, 0)));
    }

    @Test
    void onlyThePartInsideTheWindowCounts() {
        // Booked 6-9 and 11-14, but we only check 8-12. Both touch the window, never at once.
        assertEquals(1, Concurrency.peak(List.of(w(6, 9), w(11, 14)), at(8, 0), at(12, 0)));
        // Booked 6-9 and 8-14: both are active together between 8 and 9.
        assertEquals(2, Concurrency.peak(List.of(w(6, 9), w(8, 14)), at(8, 0), at(12, 0)));
    }

    @Test
    void reservationsOutsideTheWindowAreIgnored() {
        assertEquals(0, Concurrency.peak(List.of(w(6, 8), w(12, 14)), at(8, 0), at(12, 0)));
    }
}
