package com.tepeu.dto;

/**
 * Request body for POST /api/session/{id}/fork.
 * {@code messageId} 为分叉点消息（含该条及之前的历史会复制到新会话）。
 */
public class ForkSessionRequest {
    private String messageId;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
}
