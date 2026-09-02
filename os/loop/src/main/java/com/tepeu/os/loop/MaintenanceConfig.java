package com.tepeu.os.loop;

import com.tepeu.os.session.local.LedgerMetering;
import com.tepeu.os.session.Metering;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * maintenance 窗口配置：强制上限 + 独立预算条目（默认不限）。
 */
public record MaintenanceConfig(Duration maxWindow, Clock clock, Metering windowMetering) {

    public static final Duration DEFAULT_MAX_WINDOW = Duration.ofSeconds(30);

    public MaintenanceConfig {
        Objects.requireNonNull(maxWindow, "maxWindow");
        if (maxWindow.isZero() || maxWindow.isNegative()) {
            throw new IllegalArgumentException("maxWindow <= 0");
        }
        clock = clock == null ? Clock.systemUTC() : clock;
        windowMetering = windowMetering == null ? LedgerMetering.unlimited() : windowMetering;
    }

    public static MaintenanceConfig of(Duration maxWindow) {
        return new MaintenanceConfig(maxWindow, Clock.systemUTC(), LedgerMetering.unlimited());
    }
}
