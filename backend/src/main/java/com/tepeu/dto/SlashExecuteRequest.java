package com.tepeu.dto;

/**
 * 执行 Slash 命令请求体。
 * {@code command} 可为 {@code help} 或 {@code /help}；{@code args} 也可写在 command 行内。
 */
public class SlashExecuteRequest {
    private String command;
    private String workspaceId;
    private String sessionId;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
