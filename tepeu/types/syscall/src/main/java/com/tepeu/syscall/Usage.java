package com.tepeu.syscall;

import java.util.Optional;

/**
 * 一次调用的用量。{@code cost} 空 = 未知，不填 0。
 * 第一刀无缓存槽。
 */
public record Usage(long inputTokens, long outputTokens, Optional<String> cost) {

    public Usage {
        cost = cost == null ? Optional.empty() : cost;
    }

    public Usage(long inputTokens, long outputTokens) {
        this(inputTokens, outputTokens, Optional.empty());
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
