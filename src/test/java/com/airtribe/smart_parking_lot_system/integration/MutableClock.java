package com.airtribe.smart_parking_lot_system.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose time only moves when a test says so. */
class MutableClock extends Clock {
    static final Instant START = Instant.parse("2026-10-15T10:00:00Z");

    private volatile Instant now = START;

    void advance(Duration d) { now = now.plus(d); }

    void reset() { now = START; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return now; }
}
