package com.tepeu.identity;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 谁、哪个工作区、哪一次对话。取消是标志位，不是线程中断。
 */
public final class InvokeContext {
    private final Principal principal;
    private final WorkspaceId workspaceId;
    private final SessionId sessionId;
    private final AtomicBoolean cancelled;

    public InvokeContext(Principal principal, WorkspaceId workspaceId, SessionId sessionId) {
        this.principal = Objects.requireNonNull(principal, "principal");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.cancelled = new AtomicBoolean(false);
    }

    public Principal principal() {
        return principal;
    }

    public WorkspaceId workspaceId() {
        return workspaceId;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
