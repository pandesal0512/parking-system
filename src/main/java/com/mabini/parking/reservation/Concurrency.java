package com.mabini.parking.reservation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out the busiest moment inside a time window.
 *
 * Why not just count overlapping reservations? Example: 60 people booked 8:00-9:00 and another
 * 60 booked 11:00-12:00. A new booking for 8:00-12:00 overlaps all 120, but at no single moment
 * are more than 60 spaces used. Counting overlaps would wrongly say "full". The peak is the
 * correct number to compare with the capacity.
 *
 * Windows are half-open: [start, end). A reservation ending at 9:00 and one starting at 9:00
 * do NOT overlap.
 */
public final class Concurrency {

    public record Window(LocalDateTime start, LocalDateTime end) {
    }

    private record Event(LocalDateTime time, int delta) {
    }

    private Concurrency() {
    }

    /** The highest number of the given windows that are active at the same moment within [from, to). */
    public static int peak(List<Window> windows, LocalDateTime from, LocalDateTime to) {
        List<Event> events = new ArrayList<>();
        for (Window w : windows) {
            LocalDateTime start = w.start().isBefore(from) ? from : w.start();
            LocalDateTime end = w.end().isAfter(to) ? to : w.end();
            if (!start.isBefore(end)) {
                continue; // no overlap with the window we are checking
            }
            events.add(new Event(start, +1));
            events.add(new Event(end, -1));
        }

        // At the same time, process "ends" (-1) before "starts" (+1).
        events.sort(Comparator.comparing(Event::time).thenComparingInt(Event::delta));

        int current = 0;
        int peak = 0;
        for (Event event : events) {
            current += event.delta();
            peak = Math.max(peak, current);
        }
        return peak;
    }
}
