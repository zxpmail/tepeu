package com.tepeu.syscall;

import java.util.Objects;
import java.util.Optional;

/**
 * 一次调用的结果。失败不抛：{@code ok=false} + {@code errorCode}。
 * {@link Usage} 空 = 未知。
 */
public record SyscallResult(
        boolean ok,
        String output,
        Optional<String> errorCode,
        Optional<Usage> usage) {

    public SyscallResult {
        Objects.requireNonNull(output, "output");
        errorCode = errorCode == null ? Optional.empty() : errorCode;
        usage = usage == null ? Optional.empty() : usage;
    }

    public static SyscallResult success(String output) {
        return new SyscallResult(true, output, Optional.empty(), Optional.empty());
    }

    public static SyscallResult success(String output, Usage usage) {
        return new SyscallResult(true, output, Optional.empty(), Optional.of(usage));
    }

    public static SyscallResult failure(String errorCode, String message) {
        return new SyscallResult(false, message, Optional.ofNullable(errorCode), Optional.empty());
    }
}
