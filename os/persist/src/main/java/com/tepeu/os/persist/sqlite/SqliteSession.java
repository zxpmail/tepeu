package com.tepeu.os.persist.sqlite;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.session.ClaimLease;
import com.tepeu.os.session.ContentStore;
import com.tepeu.os.session.InboxMessage;
import com.tepeu.os.session.LedgerEntry;
import com.tepeu.os.session.LogReplacePort;
import com.tepeu.os.session.Priority;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionInbox;
import com.tepeu.os.session.SessionLedger;
import com.tepeu.os.session.SessionLog;
import com.tepeu.os.session.SessionRegisters;
import com.tepeu.os.session.SurfaceEpoch;
import com.tepeu.os.syscall.Usage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 一张 SQLite 会话行上的 Session 聚合 — 三 store + Inbox/claim + surface，语义对齐测试源内存实现。
 * 包内实现；对外只经 {@link SqliteSessionStore}。
 */
final class SqliteSession implements Session {

    private final SqliteSessionStore store;
    private final SessionId id;
    private final Log log = new Log();
    private final Inbox inbox = new Inbox();
    private final Ledger ledger = new Ledger();
    private final Registers registers = new Registers();
    private final Blobs blobs = new Blobs();
    private final Surface surface = new Surface();

    SqliteSession(SqliteSessionStore store, SessionId id) {
        this.store = store;
        this.id = id;
    }

    @Override
    public SessionId id() {
        return id;
    }

    @Override
    public Namespace namespace() {
        return store.tx(c -> SqliteSessionStore.namespace(SqliteSessionStore.loadRow(c, id.value())));
    }

    @Override
    public Principal owner() {
        return store.tx(c -> SqliteSessionStore.principal(SqliteSessionStore.loadRow(c, id.value())));
    }

    @Override
    public Optional<SessionId> parentId() {
        return store.tx(c -> {
            String p = SqliteSessionStore.loadRow(c, id.value()).parentId();
            return p == null ? Optional.empty() : Optional.of(new SessionId(p));
        });
    }

    @Override
    public Optional<String> forkFromEventId() {
        return store.tx(c -> Optional.ofNullable(SqliteSessionStore.loadRow(c, id.value()).forkFrom()));
    }

    @Override
    public Optional<Long> seedEndSeq() {
        return store.tx(c -> {
            long seed = SqliteSessionStore.loadRow(c, id.value()).seedEnd();
            return seed > 0 ? Optional.of(seed) : Optional.empty();
        });
    }

    @Override
    public SessionLog log() {
        return log;
    }

    @Override
    public SessionInbox inbox() {
        return inbox;
    }

    @Override
    public SessionLedger ledger() {
        return ledger;
    }

    @Override
    public SessionRegisters registers() {
        return registers;
    }

    @Override
    public LogReplacePort logReplace() {
        return surface;
    }

    @Override
    public ContentStore blobs() {
        return blobs;
    }

    @Override
    public int recover() {
        return store.tx(c -> {
            int open = 0;
            for (SessionEvent event : SqliteSessionStore.loadEvents(c, id.value())) {
                if (event.type() == SessionEventType.TOOL_CALL) {
                    open++;
                } else if (event.type() == SessionEventType.TOOL_RESULT && open > 0) {
                    open--;
                }
            }
            int unpaired = 0;
            for (int i = 0; i < open; i++) {
                appendEvent(c, SessionEventType.TOOL_RESULT, "interrupted",
                        Map.of("errorCode", "INTERRUPTED"));
                unpaired++;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT v FROM registers WHERE session_id=? AND k='loop.state'")) {
                ps.setString(1, id.value());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && !"IDLE".equals(rs.getString(1))) {
                        SqliteSessionStore.putRegister(c, id.value(), "loop.state", "IDLE");
                    }
                }
            }
            return unpaired;
        });
    }

    private long appendEvent(Connection c, SessionEventType type, String body, Map<String, String> attrs)
            throws SQLException {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        if (type == SessionEventType.END_SEED) {
            throw new IllegalArgumentException("END_SEED is fork-only");
        }
        long seq;
        try (PreparedStatement q = c.prepareStatement(
                "SELECT COALESCE(MAX(seq),0)+1 FROM events WHERE session_id=?")) {
            q.setString(1, id.value());
            try (ResultSet rs = q.executeQuery()) {
                rs.next();
                seq = rs.getLong(1);
            }
        }
        SqliteSessionStore.insertEvent(c, id.value(), seq, type, store.clock().instant().toEpochMilli(),
                body, attrs);
        boolean explicit;
        try (PreparedStatement ps = c.prepareStatement("SELECT surface_explicit FROM sessions WHERE id=?")) {
            ps.setString(1, id.value());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                explicit = rs.getInt(1) != 0;
            }
        }
        if (explicit) {
            int ordinal;
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT COALESCE(MAX(ordinal),-1)+1 FROM surface WHERE session_id=?")) {
                q.setString(1, id.value());
                try (ResultSet rs = q.executeQuery()) {
                    rs.next();
                    ordinal = rs.getInt(1);
                }
            }
            SqliteSessionStore.insertSurface(c, id.value(), ordinal, seq);
        }
        return seq;
    }

    private final class Log implements SessionLog {
        @Override
        public long append(SessionEventType type, String body, Map<String, String> attrs) {
            return store.tx(c -> appendEvent(c, type, body, attrs));
        }

        @Override
        public List<SessionEvent> readAll() {
            return store.tx(c -> SqliteSessionStore.loadEvents(c, id.value()));
        }

        @Override
        public Optional<SessionEvent> get(long seq) {
            return store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT seq, type, at_millis, body, attrs FROM events WHERE session_id=? AND seq=?")) {
                    ps.setString(1, id.value());
                    ps.setLong(2, seq);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(new SessionEvent(
                                rs.getLong(1),
                                SessionEventType.valueOf(rs.getString(2)),
                                Instant.ofEpochMilli(rs.getLong(3)),
                                rs.getString(4),
                                AttrsJson.read(rs.getString(5))));
                    }
                }
            });
        }
    }

    private final class Surface implements LogReplacePort {
        @Override
        public long replaceRange(long fromSeq, long toSeq, String checkpointBody) {
            if (fromSeq <= 0 || fromSeq > toSeq) {
                throw new IllegalArgumentException("invalid replace range: [" + fromSeq + "," + toSeq + "]");
            }
            return store.tx(c -> {
                long seed = SqliteSessionStore.loadRow(c, id.value()).seedEnd();
                if (seed > 0 && fromSeq <= seed) {
                    throw new IllegalArgumentException("replaceRange must not extend into seed zone");
                }
                long seq = appendEvent(c, SessionEventType.COMPACTION_CHECKPOINT, checkpointBody,
                        Map.of("from", String.valueOf(fromSeq), "to", String.valueOf(toSeq)));
                List<Long> visible = SqliteSessionStore.surfaceSeqs(c, id.value());
                List<Long> next = new ArrayList<>();
                boolean inserted = false;
                for (Long s : visible) {
                    if (s == seq) {
                        continue;
                    }
                    if (s >= fromSeq && s <= toSeq) {
                        if (!inserted) {
                            next.add(seq);
                            inserted = true;
                        }
                        continue;
                    }
                    next.add(s);
                }
                if (!inserted) {
                    next.add(seq);
                }
                try (PreparedStatement d = c.prepareStatement("DELETE FROM surface WHERE session_id=?")) {
                    d.setString(1, id.value());
                    d.executeUpdate();
                }
                for (int i = 0; i < next.size(); i++) {
                    SqliteSessionStore.insertSurface(c, id.value(), i, next.get(i));
                }
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE sessions SET surface_explicit=1 WHERE id=?")) {
                    u.setString(1, id.value());
                    u.executeUpdate();
                }
                SqliteSessionStore.putRegister(c, id.value(), SurfaceEpoch.KEY,
                        SurfaceEpoch.next(SqliteSessionStore.getRegister(c, id.value(), SurfaceEpoch.KEY)));
                return seq;
            });
        }

        @Override
        public List<SessionEvent> surface() {
            return store.tx(c -> {
                List<Long> seqs = SqliteSessionStore.surfaceSeqs(c, id.value());
                List<SessionEvent> all = SqliteSessionStore.loadEvents(c, id.value());
                Map<Long, SessionEvent> bySeq = new LinkedHashMap<>();
                for (SessionEvent e : all) {
                    bySeq.put(e.seq(), e);
                }
                List<SessionEvent> out = new ArrayList<>();
                for (Long seq : seqs) {
                    SessionEvent e = bySeq.get(seq);
                    if (e != null) {
                        out.add(e);
                    }
                }
                return List.copyOf(out);
            });
        }
    }

    private final class Inbox implements SessionInbox {
        @Override
        public String enqueue(String body, Optional<String> source) {
            return enqueue(body, source, Priority.NEXT);
        }

        @Override
        public String enqueue(String body, Optional<String> source, Priority priority) {
            Objects.requireNonNull(body, "body");
            Priority p = priority == null ? Priority.NEXT : priority;
            String mid = UUID.randomUUID().toString();
            store.tx(c -> {
                long enq;
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE sessions SET enqueued=enqueued+1 WHERE id=?")) {
                    u.setString(1, id.value());
                    u.executeUpdate();
                }
                try (PreparedStatement q = c.prepareStatement("SELECT enqueued FROM sessions WHERE id=?")) {
                    q.setString(1, id.value());
                    try (ResultSet rs = q.executeQuery()) {
                        rs.next();
                        enq = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO inbox(session_id, message_id, body, source, priority, claim_id, expires_at, enq) "
                                + "VALUES (?,?,?,?,?,NULL,NULL,?)")) {
                    ps.setString(1, id.value());
                    ps.setString(2, mid);
                    ps.setString(3, body);
                    ps.setString(4, source == null ? null : source.orElse(null));
                    ps.setString(5, p.name());
                    ps.setLong(6, enq);
                    ps.executeUpdate();
                }
                return null;
            });
            return mid;
        }

        @Override
        public Optional<ClaimLease> claimNext() {
            return store.tx(c -> {
                long now = store.clock().instant().toEpochMilli();
                String messageId = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT message_id FROM inbox WHERE session_id=? AND (claim_id IS NULL OR expires_at < ?) "
                                + "ORDER BY CASE priority WHEN 'NOW' THEN 0 WHEN 'NEXT' THEN 1 ELSE 2 END, enq "
                                + "LIMIT 1")) {
                    ps.setString(1, id.value());
                    ps.setLong(2, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            messageId = rs.getString(1);
                        }
                    }
                }
                if (messageId == null) {
                    return Optional.empty();
                }
                String claimId = UUID.randomUUID().toString();
                long expires = now + ClaimLease.DEFAULT_TTL.toMillis();
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE inbox SET claim_id=?, expires_at=? WHERE session_id=? AND message_id=?")) {
                    u.setString(1, claimId);
                    u.setLong(2, expires);
                    u.setString(3, id.value());
                    u.setString(4, messageId);
                    u.executeUpdate();
                }
                return Optional.of(new ClaimLease(claimId, messageId, Instant.ofEpochMilli(expires)));
            });
        }

        @Override
        public Optional<InboxMessage> claimed(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            return store.tx(c -> loadClaimed(c, claimId));
        }

        @Override
        public void ack(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM inbox WHERE session_id=? AND claim_id=?")) {
                    ps.setString(1, id.value());
                    ps.setString(2, claimId);
                    ps.executeUpdate();
                }
                return null;
            });
        }

        @Override
        public void nack(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE inbox SET claim_id=NULL, expires_at=NULL WHERE session_id=? AND claim_id=?")) {
                    ps.setString(1, id.value());
                    ps.setString(2, claimId);
                    ps.executeUpdate();
                }
                return null;
            });
        }

        @Override
        public long enqueued() {
            return store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT enqueued FROM sessions WHERE id=?")) {
                    ps.setString(1, id.value());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            });
        }

        @Override
        public boolean hasClaimableNow() {
            return store.tx(c -> {
                long now = store.clock().instant().toEpochMilli();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM inbox WHERE session_id=? AND priority='NOW' "
                                + "AND (claim_id IS NULL OR expires_at < ?) LIMIT 1")) {
                    ps.setString(1, id.value());
                    ps.setLong(2, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            });
        }

        private Optional<InboxMessage> loadClaimed(Connection c, String claimId) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT message_id, body, source, priority FROM inbox WHERE session_id=? AND claim_id=?")) {
                ps.setString(1, id.value());
                ps.setString(2, claimId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    String source = rs.getString(3);
                    return Optional.of(new InboxMessage(
                            rs.getString(1),
                            rs.getString(2),
                            Optional.ofNullable(source),
                            Priority.valueOf(rs.getString(4))));
                }
            }
        }
    }

    private final class Ledger implements SessionLedger {
        @Override
        public long record(String syscallName, Usage usage, Map<String, String> attrs) {
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(syscallName, "syscallName");
            Map<String, String> a = attrs == null ? Map.of() : attrs;
            return store.tx(c -> {
                long seq;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT COALESCE(MAX(seq),0)+1 FROM ledger WHERE session_id=?")) {
                    q.setString(1, id.value());
                    try (ResultSet rs = q.executeQuery()) {
                        rs.next();
                        seq = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ledger(session_id, seq, at_millis, syscall_name, input_tokens, output_tokens, "
                                + "cache_read, cache_write, cost, attrs) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, id.value());
                    ps.setLong(2, seq);
                    ps.setLong(3, store.clock().instant().toEpochMilli());
                    ps.setString(4, syscallName);
                    ps.setLong(5, usage.inputTokens());
                    ps.setLong(6, usage.outputTokens());
                    ps.setLong(7, usage.cacheReadTokens());
                    ps.setLong(8, usage.cacheWriteTokens());
                    ps.setString(9, usage.cost().orElse(null));
                    ps.setString(10, AttrsJson.write(a));
                    ps.executeUpdate();
                }
                return seq;
            });
        }

        @Override
        public List<LedgerEntry> readAll() {
            return store.tx(c -> {
                List<LedgerEntry> out = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT seq, at_millis, syscall_name, input_tokens, output_tokens, cache_read, cache_write, "
                                + "cost, attrs FROM ledger WHERE session_id=? ORDER BY seq")) {
                    ps.setString(1, id.value());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String cost = rs.getString(8);
                            out.add(new LedgerEntry(
                                    rs.getLong(1),
                                    Instant.ofEpochMilli(rs.getLong(2)),
                                    rs.getString(3),
                                    new Usage(rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7),
                                            Optional.ofNullable(cost)),
                                    AttrsJson.read(rs.getString(9))));
                        }
                    }
                }
                return List.copyOf(out);
            });
        }
    }

    private final class Registers implements SessionRegisters {
        @Override
        public Optional<String> get(String key) {
            Objects.requireNonNull(key, "key");
            return store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT v FROM registers WHERE session_id=? AND k=?")) {
                    ps.setString(1, id.value());
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
                    }
                }
            });
        }

        @Override
        public void put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (key.isBlank()) {
                throw new IllegalArgumentException("register key blank");
            }
            store.tx(c -> {
                SqliteSessionStore.putRegister(c, id.value(), key, value);
                return null;
            });
        }

        @Override
        public Map<String, String> snapshot() {
            return store.tx(c -> {
                LinkedHashMap<String, String> out = new LinkedHashMap<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT k, v FROM registers WHERE session_id=? ORDER BY k")) {
                    ps.setString(1, id.value());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.put(rs.getString(1), rs.getString(2));
                        }
                    }
                }
                return Map.copyOf(out);
            });
        }
    }

    private final class Blobs implements ContentStore {
        @Override
        public String put(byte[] content) {
            Objects.requireNonNull(content, "content");
            String digest = sha256Hex(content);
            store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO blobs(digest, bytes) VALUES (?,?)")) {
                    ps.setString(1, digest);
                    ps.setBytes(2, content);
                    ps.executeUpdate();
                }
                return null;
            });
            return digest;
        }

        @Override
        public Optional<byte[]> get(String digest) {
            if (digest == null) {
                return Optional.empty();
            }
            return store.tx(c -> {
                try (PreparedStatement ps = c.prepareStatement("SELECT bytes FROM blobs WHERE digest=?")) {
                    ps.setString(1, digest);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        byte[] raw = rs.getBytes(1);
                        return Optional.of(Arrays.copyOf(raw, raw.length));
                    }
                }
            });
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}
