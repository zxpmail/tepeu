package com.tepeu.os.session.memory;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionStore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存会话工厂/存取 — conformance / 测试 / MemoryAssembly。
 * 进程内 Map；发行默认 SQLite。勿当多副本或「记忆平面」。
 */
public final class InMemorySessionStore implements SessionStore {

    private final Clock clock;
    private final Map<SessionId, Session> sessions = new ConcurrentHashMap<>();

    public InMemorySessionStore() {
        this(Clock.systemUTC());
    }

    public InMemorySessionStore(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(namespace, "namespace");
        SessionId id = new SessionId(UUID.randomUUID().toString());
        Session session = new InMemorySession(id, owner, namespace,
                parentId == null ? Optional.empty() : parentId, clock);
        sessions.put(id, session);
        return session;
    }

    @Override
    public Optional<Session> get(SessionId id) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public Session fork(SessionId source, long atSeq) {
        Session origin = get(source).orElseThrow(() -> new IllegalArgumentException("unknown session"));
        if (atSeq < 0) {
            throw new IllegalArgumentException("atSeq < 0");
        }
        List<SessionEvent> audit = origin.log().readAll();
        if (atSeq == 0) {
            if (!audit.isEmpty()) {
                throw new IllegalArgumentException("atSeq 0 only for empty log");
            }
        } else if (origin.log().get(atSeq).isEmpty()) {
            throw new IllegalArgumentException("atSeq not in log: " + atSeq);
        }
        List<SessionEvent> seed = new ArrayList<>();
        for (SessionEvent event : audit) {
            if (event.seq() <= atSeq) {
                seed.add(event);
            }
        }
        List<SessionEvent> surface = new ArrayList<>();
        for (SessionEvent event : origin.logReplace().surface()) {
            if (event.seq() <= atSeq) {
                surface.add(event);
            }
        }
        Map<String, byte[]> blobs = Map.of();
        if (origin instanceof InMemorySession mem) {
            blobs = mem.blobStore().snapshot();
        }
        SessionId id = new SessionId(UUID.randomUUID().toString());
        InMemorySession forked = new InMemorySession(id, origin.owner(), origin.namespace(),
                origin.parentId(), Optional.of(source.value() + "#" + atSeq), clock,
                seed, surface, atSeq, origin.registers().snapshot(), blobs);
        sessions.put(id, forked);
        return forked;
    }
}
