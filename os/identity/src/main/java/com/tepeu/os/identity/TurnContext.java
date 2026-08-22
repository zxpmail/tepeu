package com.tepeu.os.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跨环显式上下文 — 禁止用单例 Tool.bind 代替（红线 §6-4）。
 * 取消为标志位：入口与 chunk 边界检查，不做线程 interrupt 乱杀（ADR-016 第四轮）。
 */
public final class TurnContext {
    private final Principal principal;
    private final Namespace namespace;
    private final SessionId sessionId;
    private final Optional<String> delegationId;
    private final AtomicBoolean cancelled;

    public TurnContext(
            Principal principal,
            Namespace namespace,
            SessionId sessionId,
            Optional<String> delegationId) {
        this.principal = Objects.requireNonNull(principal, "principal");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.delegationId = delegationId == null ? Optional.empty() : delegationId;
        this.cancelled = new AtomicBoolean(false);
    }

    public Principal principal() {
        return principal;
    }

    public Namespace namespace() {
        return namespace;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public Optional<String> delegationId() {
        return delegationId;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
