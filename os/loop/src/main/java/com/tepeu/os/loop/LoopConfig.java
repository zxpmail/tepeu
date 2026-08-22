package com.tepeu.os.loop;

import java.util.Objects;

/**
 * 一轮 Loop 配置。system 只转发，不在此拼装巨型提示词（红线 §6-3）。
 */
public record LoopConfig(String model, String family, String system, int maxSteps) {

    public static final int DEFAULT_MAX_STEPS = 8;

    public LoopConfig {
        Objects.requireNonNull(model, "model");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model blank");
        }
        family = family == null || family.isBlank() ? "anthropic" : family;
        system = system == null ? "" : system;
        if (maxSteps < 0) {
            throw new IllegalArgumentException("maxSteps < 0");
        }
    }

    public static LoopConfig of(String model) {
        return new LoopConfig(model, "anthropic", "", DEFAULT_MAX_STEPS);
    }
}
