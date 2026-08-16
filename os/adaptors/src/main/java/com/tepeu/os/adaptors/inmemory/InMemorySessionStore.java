package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionId;
import com.tepeu.os.kernel.session.SessionStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存会话工厂/存取 — conformance/测试用；生产默认 SQLite（ADR-016 Adaptor 表）。
 */
public final class InMemorySessionStore implements SessionStore {

    private final Map<SessionId, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(namespace, "namespace");
        SessionId id = new SessionId(UUID.randomUUID().toString());
        Session session = new InMemorySession(id, owner, namespace,
                parentId == null ? Optional.empty() : parentId);
        sessions.put(id, session);
        return session;
    }

    @Override
    public Optional<Session> get(SessionId id) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(id, "id")));
    }
}
