package com.tepeu.session.persist;

import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.Persist;
import com.tepeu.persist.PersistRecord;
import com.tepeu.session.Session;
import com.tepeu.session.SessionStore;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用 persist-api 打开会话。调用方开库、关库。
 */
public final class PersistedSessionStore implements SessionStore {

    private final Persist persist;
    private final Clock clock;

    public PersistedSessionStore(Persist persist) {
        this(persist, Clock.systemUTC());
    }

    public PersistedSessionStore(Persist persist, Clock clock) {
        this.persist = Objects.requireNonNull(persist, "persist");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Session open(SessionId id, Principal owner, WorkspaceId workspace) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(workspace, "workspace");
        String metaSpace = PersistedSession.space(id, "meta");
        Optional<PersistRecord> existing = persist.get(metaSpace, "meta");
        if (existing.isEmpty()) {
            persist.append(metaSpace, new PersistRecord("meta", Map.of(
                    "owner", owner.id().value(),
                    "workspace", workspace.value())));
            return new PersistedSession(persist, id, owner, workspace, clock);
        }
        Map<String, String> fields = existing.get().fields();
        String ownerValue = fields.get("owner");
        String workspaceValue = fields.get("workspace");
        if (ownerValue == null || ownerValue.isBlank()
                || workspaceValue == null || workspaceValue.isBlank()) {
            throw new IllegalStateException("session meta corrupt: " + id);
        }
        return new PersistedSession(
                persist,
                id,
                new Principal(new PrincipalId(ownerValue)),
                new WorkspaceId(workspaceValue),
                clock);
    }
}
