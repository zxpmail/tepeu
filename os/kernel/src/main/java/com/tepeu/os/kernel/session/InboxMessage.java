package com.tepeu.os.kernel.session;

import java.util.Objects;
import java.util.Optional;

/**
 * Inbox 中的一条待领取输入（优先级 day-one 在签名内，第四轮）。
 */
public record InboxMessage(String id, String body, Optional<String> source, Priority priority) {
    public InboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        source = source == null ? Optional.empty() : source;
        priority = priority == null ? Priority.NEXT : priority;
    }
}
