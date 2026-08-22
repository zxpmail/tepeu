package com.tepeu.os.syscall;

import java.util.Map;
import java.util.Objects;

/**
 * 一次 syscall 请求 — name 如 llm.stream / execution.run / read_file（命名族见底板 §3.2）。
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
