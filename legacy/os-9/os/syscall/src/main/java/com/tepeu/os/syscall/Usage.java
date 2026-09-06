package com.tepeu.os.syscall;

import java.util.Optional;

/**
 * 一次调用的用量 — 供 ledger / Metering 记账，不是计费引擎。
 * <p>
 * inclusive 双轨第一步：{@code inputTokens} 为非缓存输入；
 * {@link #totalInput()} = input + cacheRead + cacheWrite。
 * {@code cost} 空 = 未知/n/a（禁止填 0 假装有价）。
 */
public record Usage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        Optional<String> cost) {

    public Usage {
        cost = cost == null ? Optional.empty() : cost;
    }

    public Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        this(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, Optional.empty());
    }

    public long totalInput() {
        return inputTokens + cacheReadTokens + cacheWriteTokens;
    }

    public long totalTokens() {
        return totalInput() + outputTokens;
    }
}
