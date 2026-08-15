package com.tepeu.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/chat/stream}.
 *
 * <ul>
 *   <li>{@code message} — the user's prompt (required, non-blank)</li>
 *   <li>{@code workspaceId} — the owning workspace (required when sessionId is null)</li>
 *   <li>{@code sessionId} — existing session to append to (optional)</li>
 *   <li>{@code provider} — LLM provider id: {@code openai|anthropic|ollama}</li>
 *   <li>{@code fileRefs} — optional workspace-relative paths from {@code @} mentions</li>
 *   <li>{@code skillRefs} — optional skill slugs from {@code /} or {@code @} (也可从 message 解析)</li>
 *   <li>{@code idempotencyKey} — optional；相同 key 在 TTL 内不重复调用模型，回放上次结果</li>
 * </ul>
 */
public class ChatRequest {
    private String message;
    private String workspaceId;
    private String sessionId;
    private String provider;
    /** 用户通过 @ 引用的工作区相对路径，注入本轮上下文 */
    private List<String> fileRefs;
    /** 用户本轮显式调用的技能 slug */
    private List<String> skillRefs;
    /** 客户端/Agent 生成的幂等键；判断重复由服务端完成 */
    private String idempotencyKey;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public List<String> getFileRefs() { return fileRefs; }
    public void setFileRefs(List<String> fileRefs) { this.fileRefs = fileRefs; }
    public List<String> getSkillRefs() { return skillRefs; }
    public void setSkillRefs(List<String> skillRefs) { this.skillRefs = skillRefs; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
