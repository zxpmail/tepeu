package com.tepeu.os.loop;

import java.util.Objects;

/**
 * 一轮 Loop 配置。system 只转发，不在此拼装巨型提示词（红线 §6-3）。
 * compactOverflow：live surface 条数超过则在下一次 llm.generate 前压一步；0 = 关闭。
 */
public record LoopConfig(
        String model,
        String family,
        String system,
        int maxSteps,
        int compactOverflow,
        int compactKeepLast) {

    public static final int DEFAULT_MAX_STEPS = 8;
    public static final int DEFAULT_COMPACT_OVERFLOW = 40;
    public static final int DEFAULT_COMPACT_KEEP_LAST = 4;

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
        if (compactOverflow < 0) {
            throw new IllegalArgumentException("compactOverflow < 0");
        }
        if (compactKeepLast < 1) {
            throw new IllegalArgumentException("compactKeepLast < 1");
        }
        if (compactOverflow > 0 && compactKeepLast >= compactOverflow) {
            throw new IllegalArgumentException("compactKeepLast must be < compactOverflow");
        }
    }

    public LoopConfig(String model, String family, String system, int maxSteps) {
        this(model, family, system, maxSteps, DEFAULT_COMPACT_OVERFLOW, DEFAULT_COMPACT_KEEP_LAST);
    }

    public static LoopConfig of(String model) {
        return new LoopConfig(model, "anthropic", "", DEFAULT_MAX_STEPS);
    }
}
