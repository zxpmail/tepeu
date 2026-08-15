package com.tepeu.model;

import java.time.LocalDateTime;

/**
 * A single chat turn persisted under a {@link Session}.
 *
 * <p>{@code role} is {@code user|assistant|system}（DB CHECK）。工具过程以
 * {@code system} + {@code TEPEU_TOOL_V1:} 前缀持久化，刷新后前端解码为工具卡片。
 */
public class Message {
    private String id;
    private String sessionId;
    private String role;        // user | assistant | system
    private String content;
    private LocalDateTime createdAt;

    public Message() {}

    public Message(String id, String sessionId, String role, String content) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
