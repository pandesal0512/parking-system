package com.mabini.parking.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * The system clock, plus an offset that the demo page can move forward.
 * The offset is zero unless app.demo.time-control=true AND the super admin advances it.
 * It lives in memory only: restarting the app puts the clock back to real time.
 */
public class OffsetClock extends Clock {

    private final Clock base;
    private volatile Duration offset = Duration.ZERO;

    public OffsetClock(Clock base) {
        this.base = base;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        OffsetClock copy = new OffsetClock(base.withZone(zone));
        copy.offset = this.offset;
        return copy;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset);
    }

    public Duration getOffset() {
        return offset;
    }

    public synchronized void advance(Duration amount) {
        this.offset = this.offset.plus(amount);
    }

    public void reset() {
        this.offset = Duration.ZERO;
    }
}
