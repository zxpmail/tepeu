package com.tepeu.os.session.sqlite;

import com.tepeu.os.identity.AgentKind;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite WAL SessionStore — 发行默认插头。过同一套 SessionConformance。
 * 单连接 + 互斥：单写者本机；多副本 fencing 仍远期。
 */
public final class SqliteSessionStore implements SessionStore, AutoCloseable {

    private final Path file;
    private final Clock clock;
    private final Connection conn;
    private final Object lock = new Object();
    private final SqliteAuditSink audit;
    private volatile boolean closed;

    public SqliteSessionStore(Path file) {
        this(file, Clock.systemUTC());
    }

    public SqliteSessionStore(Path file, Clock clock) {
        this.file = Objects.requireNonNull(file, "file");
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.conn = Sqlite.open(this.file);
        Sqlite.migrate(this.conn);
        this.audit = new SqliteAuditSink(this);
    }

    public Path file() {
        return file;
    }

    public AuditSink audit() {
        return audit;
    }

    Clock clock() {
        return clock;
    }

    interface Sql<T> {
        T run(Connection c) throws SQLException;
    }

    <T> T tx(Sql<T> sql) {
        synchronized (lock) {
            if (closed) {
                throw new SqliteStoreException("store closed", null);
            }
            try {
                conn.setAutoCommit(false);
                T result = sql.run(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new SqliteStoreException(e);
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // keep closed-fail visible on next op
                }
            }
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // original exception already in flight
        }
    }

    @Override
    public Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(namespace, "namespace");
        SessionId id = new SessionId(UUID.randomUUID().toString());
        Optional<SessionId> parent = parentId == null ? Optional.empty() : parentId;
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sessions(id, owner_id, owner_kind, owner_display, workspace_id, tenant_id, parent_id) "
                            + "VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, id.value());
                ps.setString(2, owner.id().value());
                ps.setString(3, owner.kind().name());
                ps.setString(4, owner.displayName().orElse(null));
                ps.setString(5, namespace.workspaceId().value());
                ps.setString(6, namespace.tenantId().orElse(null));
                ps.setString(7, parent.map(SessionId::value).orElse(null));
                ps.executeUpdate();
            }
            return null;
        });
        return new SqliteSession(this, id);
    }

    @Override
    public Optional<Session> get(SessionId id) {
        Objects.requireNonNull(id, "id");
        return tx(c -> exists(c, id.value()) ? Optional.of(new SqliteSession(this, id)) : Optional.empty());
    }

    @Override
    public Session fork(SessionId source, long atSeq) {
        Objects.requireNonNull(source, "source");
        if (atSeq < 0) {
            throw new IllegalArgumentException("atSeq < 0");
        }
        SessionId id = new SessionId(UUID.randomUUID().toString());
        tx(c -> {
            if (!exists(c, source.value())) {
                throw new IllegalArgumentException("unknown session");
            }
            List<SessionEvent> auditLog = loadEvents(c, source.value());
            if (atSeq == 0) {
                if (!auditLog.isEmpty()) {
                    throw new IllegalArgumentException("atSeq 0 only for empty log");
                }
            } else if (auditLog.stream().noneMatch(e -> e.seq() == atSeq)) {
                throw new IllegalArgumentException("atSeq not in log: " + atSeq);
            }
            Row src = loadRow(c, source.value());
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO sessions(id, owner_id, owner_kind, owner_display, workspace_id, tenant_id, "
                            + "parent_id, fork_from, seed_end, surface_explicit, enqueued) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,1,0)")) {
                ps.setString(1, id.value());
                ps.setString(2, src.ownerId);
                ps.setString(3, src.ownerKind);
                ps.setString(4, src.ownerDisplay);
                ps.setString(5, src.workspaceId);
                ps.setString(6, src.tenantId);
                ps.setString(7, src.parentId);
                ps.setString(8, source.value() + "#" + atSeq);
                ps.setLong(9, 0);
                ps.executeUpdate();
            }
            long last = 0;
            for (SessionEvent event : auditLog) {
                if (event.seq() > atSeq) {
                    continue;
                }
                insertEvent(c, id.value(), event.seq(), event.type(), event.at().toEpochMilli(),
                        event.body(), event.attrs());
                last = event.seq();
            }
            if (atSeq > 0) {
                if (last != atSeq) {
                    throw new IllegalArgumentException("seed atSeq not in log: " + atSeq);
                }
                long boundary = last + 1;
                insertEvent(c, id.value(), boundary, SessionEventType.END_SEED, clock.instant().toEpochMilli(),
                        "end-seed", Map.of("from", String.valueOf(atSeq)));
                try (PreparedStatement u = c.prepareStatement("UPDATE sessions SET seed_end=? WHERE id=?")) {
                    u.setLong(1, boundary);
                    u.setString(2, id.value());
                    u.executeUpdate();
                }
            }
            List<Long> surface = surfaceSeqs(c, source.value());
            int ordinal = 0;
            for (Long seq : surface) {
                if (seq <= atSeq) {
                    insertSurface(c, id.value(), ordinal++, seq);
                }
            }
            try (PreparedStatement rs = c.prepareStatement("SELECT k, v FROM registers WHERE session_id=?")) {
                rs.setString(1, source.value());
                try (ResultSet r = rs.executeQuery()) {
                    while (r.next()) {
                        String key = r.getString(1);
                        if ("loop.state".equals(key)) {
                            continue;
                        }
                        putRegister(c, id.value(), key, r.getString(2));
                    }
                }
            }
            if (atSeq > 0) {
                putRegister(c, id.value(), "fork.seedEnd", String.valueOf(atSeq + 1));
            }
            return null;
        });
        return new SqliteSession(this, id);
    }

    static boolean exists(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sessions WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static Row loadRow(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT owner_id, owner_kind, owner_display, workspace_id, tenant_id, parent_id, "
                        + "fork_from, seed_end FROM sessions WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("unknown session");
                }
                return new Row(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getLong(8));
            }
        }
    }

    static List<SessionEvent> loadEvents(Connection c, String sessionId) throws SQLException {
        List<SessionEvent> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq, type, at_millis, body, attrs FROM events WHERE session_id=? ORDER BY seq")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SessionEvent(
                            rs.getLong(1),
                            SessionEventType.valueOf(rs.getString(2)),
                            java.time.Instant.ofEpochMilli(rs.getLong(3)),
                            rs.getString(4),
                            AttrsJson.read(rs.getString(5))));
                }
            }
        }
        return out;
    }

    static List<Long> surfaceSeqs(Connection c, String sessionId) throws SQLException {
        boolean explicit;
        try (PreparedStatement ps = c.prepareStatement("SELECT surface_explicit FROM sessions WHERE id=?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("unknown session");
                }
                explicit = rs.getInt(1) != 0;
            }
        }
        List<Long> seqs = new ArrayList<>();
        if (!explicit) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seq FROM events WHERE session_id=? AND type<>'END_SEED' ORDER BY seq")) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        seqs.add(rs.getLong(1));
                    }
                }
            }
            return seqs;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq FROM surface WHERE session_id=? ORDER BY ordinal")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seqs.add(rs.getLong(1));
                }
            }
        }
        return seqs;
    }

    static void insertEvent(Connection c, String sessionId, long seq, SessionEventType type, long at,
            String body, Map<String, String> attrs) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events(session_id, seq, type, type_version, at_millis, body, attrs) "
                        + "VALUES (?,?,?,1,?,?,?)")) {
            ps.setString(1, sessionId);
            ps.setLong(2, seq);
            ps.setString(3, type.name());
            ps.setLong(4, at);
            ps.setString(5, body);
            ps.setString(6, AttrsJson.write(attrs));
            ps.executeUpdate();
        }
    }

    static void insertSurface(Connection c, String sessionId, int ordinal, long seq) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO surface(session_id, ordinal, seq) VALUES (?,?,?)")) {
            ps.setString(1, sessionId);
            ps.setInt(2, ordinal);
            ps.setLong(3, seq);
            ps.executeUpdate();
        }
    }

    static String getRegister(Connection c, String sessionId, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v FROM registers WHERE session_id=? AND k=?")) {
            ps.setString(1, sessionId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    static void putRegister(Connection c, String sessionId, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO registers(session_id, k, v) VALUES (?,?,?) "
                        + "ON CONFLICT(session_id, k) DO UPDATE SET v=excluded.v")) {
            ps.setString(1, sessionId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    static Principal principal(Row row) {
        return new Principal(new PrincipalId(row.ownerId), AgentKind.valueOf(row.ownerKind),
                Optional.ofNullable(row.ownerDisplay));
    }

    static Namespace namespace(Row row) {
        return new Namespace(new WorkspaceId(row.workspaceId), Optional.ofNullable(row.tenantId));
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                conn.close();
            } catch (SQLException e) {
                throw new SqliteStoreException("close", e);
            }
        }
    }

    record Row(String ownerId, String ownerKind, String ownerDisplay, String workspaceId, String tenantId,
            String parentId, String forkFrom, long seedEnd) {
    }
}
