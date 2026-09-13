package com.tepeu.session;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 一条只追加的对话事实。{@code attrs} 不可变副本，空合法。 */
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
