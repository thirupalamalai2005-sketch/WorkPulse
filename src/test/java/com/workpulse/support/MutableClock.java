package com.workpulse.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** A Clock tests can move, so attendance/leave rules are deterministic. */
public class MutableClock extends Clock {
    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Sets the time using a local date-time in this clock's zone, e.g. "2026-03-04T09:10". */
    public void setLocal(String isoLocalDateTime) {
        this.instant = LocalDateTime.parse(isoLocalDateTime).atZone(zone).toInstant();
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(instant, z); }
    @Override public Instant instant() { return instant; }
}
