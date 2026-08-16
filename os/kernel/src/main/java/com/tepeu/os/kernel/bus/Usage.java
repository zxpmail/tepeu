package com.tepeu.os.kernel.bus;

/**
 * 一次调用的用量 — inclusive 双轨语义第一步（OpenCode 不变式）：
 * inputTokens 为非缓存输入；totalInput() = inputTokens + cacheRead + cacheWrite。
 * 完整协议（cost/分层计价）挂账于 ledger 切片。
 */
public record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {

    public long totalInput() {
        return inputTokens + cacheReadTokens + cacheWriteTokens;
    }

    public long totalTokens() {
        return totalInput() + outputTokens;
    }
}
