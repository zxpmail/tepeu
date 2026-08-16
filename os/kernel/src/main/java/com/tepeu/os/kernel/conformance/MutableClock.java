package com.tepeu.os.kernel.conformance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可拨时钟 — TTL/超时类用例拨时钟而非真 sleep（Netty Ticker 先例）。起点 EPOCH 保证确定性。
 */
public final class MutableClock extends Clock {

    private Instant now;

    public MutableClock() {
        this(Instant.EPOCH);
    }

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
