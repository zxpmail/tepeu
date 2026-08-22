package com.tepeu.os.syscall;

import java.util.Optional;

/**
 * 一次调用的用量 — inclusive 双轨语义第一步（OpenCode 不变式）：
 * inputTokens 为非缓存输入；totalInput() = inputTokens + cacheRead + cacheWrite。
 * cost 空 = 未知/n/a（禁止假装有价）。
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
