package com.tepeu.os.kernel.session;

import java.util.Objects;
import java.util.Optional;

/**
 * Inbox 中的一条待领取输入。
 */
public record InboxMessage(String id, String body, Optional<String> source) {
    public InboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        source = source == null ? Optional.empty() : source;
    }
}
