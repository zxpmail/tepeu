package com.tepeu.dto;

/**
 * 更新会话请求体 — 目前仅支持改标题。
 * 关联：SessionController PATCH /api/session/{id}。
 */
public class UpdateSessionRequest {
    private String title;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
