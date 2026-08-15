package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionId;
import com.tepeu.os.kernel.session.SessionRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 内存会话注册表。 */
public final class InMemorySessionRegistry implements SessionRegistry {

    private final Map<SessionId, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        SessionId id = new SessionId(UUID.randomUUID().toString());
        InMemorySession session = new InMemorySession(id, owner, namespace, parentId);
        sessions.put(id, session);
        return session;
    }

    @Override
    public Optional<Session> get(SessionId id) {
        return Optional.ofNullable(sessions.get(id));
    }
}
