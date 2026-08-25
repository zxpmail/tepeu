package com.tepeu.os.syscall;

import java.util.Map;
import java.util.Objects;

/**
 * 一次 syscall 请求 — {@code name}（命名族见底板 §3.2，如 {@code llm.generate}、{@code execution.fs.read}）
 * + {@code args}（不可变副本；空 Map 合法）。
 * <p>
 * 本类型不做校验业务语义；谁可调用由 Policy，隔离由 execution 囚笼。
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
