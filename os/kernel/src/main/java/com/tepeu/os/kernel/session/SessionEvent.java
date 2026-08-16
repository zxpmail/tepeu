package com.tepeu.os.kernel.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 仅追加的会话事实 — 禁止承载明文 secret（红线 §4-3）。
 */
public record SessionEvent(
        long seq,
        SessionEventType type,
        Instant at,
        String body,
        Map<String, String> attrs) {

    public SessionEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(body, "body");
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }

    public Optional<String> attr(String key) {
        return Optional.ofNullable(attrs.get(key));
    }
}
