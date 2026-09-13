package com.tepeu.session;

import java.util.Objects;

/** 收件箱里一条待领或已领的输入。 */
public record InboxMessage(String id, String body, Priority priority) {

    public InboxMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(priority, "priority");
    }
}
