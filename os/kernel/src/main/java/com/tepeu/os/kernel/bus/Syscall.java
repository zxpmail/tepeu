package com.tepeu.os.kernel.bus;

import java.util.Map;
import java.util.Objects;

/**
 * 一次 syscall 请求 — name 如 read_file / llm.stream / run_command。
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
