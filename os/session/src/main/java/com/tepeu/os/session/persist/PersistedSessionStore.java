package com.tepeu.os.session.persist;

import com.tepeu.os.identity.AgentKind;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 会话适配器 — SQL 在这里，Spring JDBC 执行。 */
final class PersistedSessionStore implements SessionStore {

    private final SessionDb db;
    private final Clock clock;

    PersistedSessionStore(SessionDb db, Clock clock) {
        this.db = db;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    Clock clock() {
        return clock;
    }

    JdbcTemplate jdbc() {
        return db.jdbc;
    }

    <T> T tx(TransactionCallback<T> work) {
        return db.tx(work);
    }

    @Override
    public Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(namespace, "namespace");
        SessionId id = new SessionId(UUID.randomUUID().toString());
        Optional<SessionId> parent = parentId == null ? Optional.empty() : parentId;
        tx(status -> {
            db.jdbc.update(
                    "INSERT INTO sessions(id, owner_id, owner_kind, owner_display, workspace_id, tenant_id, parent_id) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    id.value(),
                    owner.id().value(),
                    owner.kind().name(),
                    owner.displayName().orElse(null),
                    namespace.workspaceId().value(),
                    namespace.tenantId().orElse(null),
                    parent.isPresent() ? parent.get().value() : null);
            return null;
        });
        return new PersistedSession(this, id);
    }

    @Override
    public Optional<Session> get(SessionId id) {
        Objects.requireNonNull(id, "id");
        return tx(status -> exists(db.jdbc, id.value())
                ? Optional.of(new PersistedSession(this, id))
                : Optional.empty());
    }

    @Override
    public Session fork(SessionId source, long atSeq) {
        Objects.requireNonNull(source, "source");
        if (atSeq < 0) {
            throw new IllegalArgumentException("atSeq < 0");
        }
        SessionId id = new SessionId(UUID.randomUUID().toString());
        tx(status -> {
            JdbcTemplate jdbc = db.jdbc;
            if (!exists(jdbc, source.value())) {
                throw new IllegalArgumentException("unknown session");
            }
            List<SessionEvent> auditLog = loadEvents(jdbc, source.value());
            if (atSeq == 0) {
                if (!auditLog.isEmpty()) {
                    throw new IllegalArgumentException("atSeq 0 only for empty log");
                }
            } else if (auditLog.stream().noneMatch(e -> e.seq() == atSeq)) {
                throw new IllegalArgumentException("atSeq not in log: " + atSeq);
            }
            Row src = loadRow(jdbc, source.value());
            jdbc.update(
                    "INSERT INTO sessions(id, owner_id, owner_kind, owner_display, workspace_id, tenant_id, "
                            + "parent_id, fork_from, seed_end, surface_explicit, enqueued) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,1,0)",
                    id.value(),
                    src.ownerId,
                    src.ownerKind,
                    src.ownerDisplay,
                    src.workspaceId,
                    src.tenantId,
                    src.parentId,
                    source.value() + "#" + atSeq,
                    0L);
            long last = 0;
            for (SessionEvent event : auditLog) {
                if (event.seq() > atSeq) {
                    continue;
                }
                insertEvent(jdbc, id.value(), event.seq(), event.type(), event.at().toEpochMilli(),
                        event.body(), event.attrs());
                last = event.seq();
            }
            if (atSeq > 0) {
                if (last != atSeq) {
                    throw new IllegalArgumentException("seed atSeq not in log: " + atSeq);
                }
                long boundary = last + 1;
                insertEvent(jdbc, id.value(), boundary, SessionEventType.END_SEED, clock.instant().toEpochMilli(),
                        "end-seed", Map.of("from", String.valueOf(atSeq)));
                jdbc.update("UPDATE sessions SET seed_end=? WHERE id=?", boundary, id.value());
            }
            List<Long> surface = surfaceSeqs(jdbc, source.value());
            int ordinal = 0;
            for (Long seq : surface) {
                if (seq <= atSeq) {
                    insertSurface(jdbc, id.value(), ordinal++, seq);
                }
            }
            jdbc.query("SELECT k, v FROM registers WHERE session_id=?", rs -> {
                String key = rs.getString(1);
                if (!"loop.state".equals(key)) {
                    putRegister(jdbc, id.value(), key, rs.getString(2));
                }
            }, source.value());
            if (atSeq > 0) {
                putRegister(jdbc, id.value(), "fork.seedEnd", String.valueOf(atSeq + 1));
            }
            return null;
        });
        return new PersistedSession(this, id);
    }

    static boolean exists(JdbcTemplate jdbc, String id) {
        return !jdbc.query("SELECT 1 FROM sessions WHERE id=?", (rs, i) -> 1, id).isEmpty();
    }

    static Row loadRow(JdbcTemplate jdbc, String id) {
        List<Row> rows = jdbc.query(
                "SELECT owner_id, owner_kind, owner_display, workspace_id, tenant_id, parent_id, "
                        + "fork_from, seed_end FROM sessions WHERE id=?",
                (rs, i) -> new Row(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getLong(8)),
                id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("unknown session");
        }
        return rows.get(0);
    }

    static List<SessionEvent> loadEvents(JdbcTemplate jdbc, String sessionId) {
        return jdbc.query(
                "SELECT seq, type, at_millis, body, attrs FROM events WHERE session_id=? ORDER BY seq",
                (rs, i) -> new SessionEvent(
                        rs.getLong(1),
                        SessionEventType.valueOf(rs.getString(2)),
                        java.time.Instant.ofEpochMilli(rs.getLong(3)),
                        rs.getString(4),
                        AttrsJson.read(rs.getString(5))),
                sessionId);
    }

    static List<Long> surfaceSeqs(JdbcTemplate jdbc, String sessionId) {
        List<Integer> flags = jdbc.query("SELECT surface_explicit FROM sessions WHERE id=?",
                (rs, i) -> rs.getInt(1), sessionId);
        if (flags.isEmpty()) {
            throw new IllegalArgumentException("unknown session");
        }
        boolean explicit = flags.get(0) != 0;
        if (!explicit) {
            return jdbc.query(
                    "SELECT seq FROM events WHERE session_id=? AND type<>'END_SEED' ORDER BY seq",
                    (rs, i) -> rs.getLong(1),
                    sessionId);
        }
        return jdbc.query(
                "SELECT seq FROM surface WHERE session_id=? ORDER BY ordinal",
                (rs, i) -> rs.getLong(1),
                sessionId);
    }

    static void insertEvent(JdbcTemplate jdbc, String sessionId, long seq, SessionEventType type, long at,
            String body, Map<String, String> attrs) {
        jdbc.update(
                "INSERT INTO events(session_id, seq, type, type_version, at_millis, body, attrs) "
                        + "VALUES (?,?,?,1,?,?,?)",
                sessionId, seq, type.name(), at, body, AttrsJson.write(attrs));
    }

    static void insertSurface(JdbcTemplate jdbc, String sessionId, int ordinal, long seq) {
        jdbc.update("INSERT INTO surface(session_id, ordinal, seq) VALUES (?,?,?)", sessionId, ordinal, seq);
    }

    static String getRegister(JdbcTemplate jdbc, String sessionId, String key) {
        List<String> rows = jdbc.query("SELECT v FROM registers WHERE session_id=? AND k=?",
                (rs, i) -> rs.getString(1), sessionId, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    static void putRegister(JdbcTemplate jdbc, String sessionId, String key, String value) {
        jdbc.update(
                "INSERT INTO registers(session_id, k, v) VALUES (?,?,?) "
                        + "ON CONFLICT(session_id, k) DO UPDATE SET v=excluded.v",
                sessionId, key, value);
    }

    static Principal principal(Row row) {
        return new Principal(new PrincipalId(row.ownerId), AgentKind.valueOf(row.ownerKind),
                Optional.ofNullable(row.ownerDisplay));
    }

    static Namespace namespace(Row row) {
        return new Namespace(new WorkspaceId(row.workspaceId), Optional.ofNullable(row.tenantId));
    }

    record Row(String ownerId, String ownerKind, String ownerDisplay, String workspaceId, String tenantId,
            String parentId, String forkFrom, long seedEnd) {
    }
}
