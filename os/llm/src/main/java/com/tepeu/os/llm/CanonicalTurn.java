package com.tepeu.os.llm;

import com.tepeu.os.session.SessionEventType;

import java.util.Map;
import java.util.Objects;

/**
 * derive(log) 的一拍 — 对 7 类事件全定义。
 */
public record CanonicalTurn(
        CanonicalRole role,
        SessionEventType source,
        String body,
        Map<String, String> attrs) {

    public CanonicalTurn {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(body, "body");
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }
}
