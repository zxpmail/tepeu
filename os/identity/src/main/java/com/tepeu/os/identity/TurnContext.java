package com.tepeu.os.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跨环显式上下文 — syscall / Loop / Policy 的公共入参。
 * <p>
 * 红线（底板 §6-4）：禁止用单例 Tool.bind 或隐式 ThreadLocal 代替本对象。
 * 取消为标志位：入口与流式 chunk 边界检查；不做线程 {@code interrupt} 乱杀（ADR-016 第四轮）。
 */
public final class TurnContext {
    private final Principal principal;
    private final Namespace namespace;
    private final SessionId sessionId;
    /** 子代理 / 委派链 id；空 = 主会话本 turn。 */
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

    /** 协作取消：置位后下游在边界处停；不保证打断已在飞的阻塞 IO。 */
    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
