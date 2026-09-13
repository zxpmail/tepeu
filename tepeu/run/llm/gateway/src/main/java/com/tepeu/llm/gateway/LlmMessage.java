package com.tepeu.llm.gateway;

import java.util.Objects;

/** 一条模型可见消息。{@code body} 非空，空白合法。 */
public record LlmMessage(Role role, String body) {
    public LlmMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(body, "body");
    }
}
