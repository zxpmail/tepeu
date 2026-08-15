package com.tepeu.os.kernel.bus;

import java.util.Objects;
import java.util.Optional;

/**
 * syscall 结果。
 */
public record SyscallResult(boolean ok, String output, Optional<String> errorCode) {
    public SyscallResult {
        Objects.requireNonNull(output, "output");
        errorCode = errorCode == null ? Optional.empty() : errorCode;
    }

    public static SyscallResult success(String output) {
        return new SyscallResult(true, output, Optional.empty());
    }

    public static SyscallResult failure(String errorCode, String message) {
        return new SyscallResult(false, message, Optional.ofNullable(errorCode));
    }
}
