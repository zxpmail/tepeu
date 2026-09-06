package com.tepeu.syscall;

import java.util.Map;
import java.util.Objects;

/**
 * 一次具名操作。{@code name} 非空、非空白。{@code args} 不可变副本，空合法。
 * 不做业务校验。
 */
public record Syscall(String name, Map<String, String> args) {
    public Syscall {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("syscall name blank");
        }
        args = args == null ? Map.of() : Map.copyOf(args);
    }
}
