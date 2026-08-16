package com.tepeu.os.kernel.bus;

import java.util.Objects;
import java.util.Optional;

/**
 * syscall 结果 — 计量槽为基座字段（TriniOS/AIOS 两票收敛，ADR-016 挂账落码）；
 * 失败不抛穿：ok=false + errorCode 即失败可见。
 */
public record SyscallResult(
        boolean ok,
        String output,
        Optional<String> errorCode,
        Optional<Usage> usage,
        Optional<Long> latencyMs) {

    public SyscallResult {
        Objects.requireNonNull(output, "output");
        errorCode = errorCode == null ? Optional.empty() : errorCode;
        usage = usage == null ? Optional.empty() : usage;
        latencyMs = latencyMs == null ? Optional.empty() : latencyMs;
    }

    public static SyscallResult success(String output) {
        return new SyscallResult(true, output, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static SyscallResult success(String output, Usage usage, long latencyMs) {
        return new SyscallResult(true, output, Optional.empty(), Optional.of(usage), Optional.of(latencyMs));
    }

    public static SyscallResult failure(String errorCode, String message) {
        return new SyscallResult(false, message, Optional.ofNullable(errorCode), Optional.empty(), Optional.empty());
    }
}
