package com.tepeu.os.session.persist;

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
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 一会话行上的聚合。对外只经 {@link SessionPersistence}。 */
final class PersistedSession implements Session {

    private final PersistedSessionStore store;
    private final SessionId id;
    private final Log log = new Log();
    private final Inbox inbox = new Inbox();
    private final Ledger ledger = new Ledger();
    private final Registers registers = new Registers();
    private final Blobs blobs = new Blobs();
    private final Surface surface = new Surface();

    PersistedSession(PersistedSessionStore store, SessionId id) {
        this.store = store;
        this.id = id;
    }

    @Override
    public SessionId id() {
        return id;
    }

    @Override
    public Namespace namespace() {
        return store.tx(status -> PersistedSessionStore.namespace(
                PersistedSessionStore.loadRow(store.jdbc(), id.value())));
    }

    @Override
    public Principal owner() {
        return store.tx(status -> PersistedSessionStore.principal(
                PersistedSessionStore.loadRow(store.jdbc(), id.value())));
    }

    @Override
    public Optional<SessionId> parentId() {
        return store.tx(status -> {
            String p = PersistedSessionStore.loadRow(store.jdbc(), id.value()).parentId();
            return p == null ? Optional.empty() : Optional.of(new SessionId(p));
        });
    }

    @Override
    public Optional<String> forkFromEventId() {
        return store.tx(status -> Optional.ofNullable(
                PersistedSessionStore.loadRow(store.jdbc(), id.value()).forkFrom()));
    }

    @Override
    public Optional<Long> seedEndSeq() {
        return store.tx(status -> {
            long seed = PersistedSessionStore.loadRow(store.jdbc(), id.value()).seedEnd();
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
        return store.tx(status -> {
            JdbcTemplate jdbc = store.jdbc();
            int open = 0;
            for (SessionEvent event : PersistedSessionStore.loadEvents(jdbc, id.value())) {
                if (event.type() == SessionEventType.TOOL_CALL) {
                    open++;
                } else if (event.type() == SessionEventType.TOOL_RESULT && open > 0) {
                    open--;
                }
            }
            int unpaired = 0;
            for (int i = 0; i < open; i++) {
                appendEvent(jdbc, SessionEventType.TOOL_RESULT, "interrupted",
                        Map.of("errorCode", "INTERRUPTED"));
                unpaired++;
            }
            List<String> state = jdbc.query(
                    "SELECT v FROM registers WHERE session_id=? AND k='loop.state'",
                    (rs, n) -> rs.getString(1), id.value());
            if (!state.isEmpty() && !"IDLE".equals(state.get(0))) {
                PersistedSessionStore.putRegister(jdbc, id.value(), "loop.state", "IDLE");
            }
            return unpaired;
        });
    }

    private long appendEvent(JdbcTemplate jdbc, SessionEventType type, String body, Map<String, String> attrs) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        if (type == SessionEventType.END_SEED) {
            throw new IllegalArgumentException("END_SEED is fork-only");
        }
        long seq = jdbc.queryForObject(
                "SELECT COALESCE(MAX(seq),0)+1 FROM events WHERE session_id=?", Long.class, id.value());
        PersistedSessionStore.insertEvent(jdbc, id.value(), seq, type, store.clock().instant().toEpochMilli(),
                body, attrs);
        boolean explicit = jdbc.queryForObject(
                "SELECT surface_explicit FROM sessions WHERE id=?", Integer.class, id.value()) != 0;
        if (explicit) {
            int ordinal = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(ordinal),-1)+1 FROM surface WHERE session_id=?",
                    Integer.class, id.value());
            PersistedSessionStore.insertSurface(jdbc, id.value(), ordinal, seq);
        }
        return seq;
    }

    private final class Log implements SessionLog {
        @Override
        public long append(SessionEventType type, String body, Map<String, String> attrs) {
            return store.tx(status -> appendEvent(store.jdbc(), type, body, attrs));
        }

        @Override
        public List<SessionEvent> readAll() {
            return store.tx(status -> PersistedSessionStore.loadEvents(store.jdbc(), id.value()));
        }

        @Override
        public Optional<SessionEvent> get(long seq) {
            return store.tx(status -> {
                List<SessionEvent> rows = store.jdbc().query(
                        "SELECT seq, type, at_millis, body, attrs FROM events WHERE session_id=? AND seq=?",
                        (rs, i) -> new SessionEvent(
                                rs.getLong(1),
                                SessionEventType.valueOf(rs.getString(2)),
                                Instant.ofEpochMilli(rs.getLong(3)),
                                rs.getString(4),
                                AttrsJson.read(rs.getString(5))),
                        id.value(), seq);
                return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
            });
        }
    }

    private final class Surface implements LogReplacePort {
        @Override
        public long replaceRange(long fromSeq, long toSeq, String checkpointBody) {
            if (fromSeq <= 0 || fromSeq > toSeq) {
                throw new IllegalArgumentException("invalid replace range: [" + fromSeq + "," + toSeq + "]");
            }
            return store.tx(status -> {
                JdbcTemplate jdbc = store.jdbc();
                long seed = PersistedSessionStore.loadRow(jdbc, id.value()).seedEnd();
                if (seed > 0 && fromSeq <= seed) {
                    throw new IllegalArgumentException("replaceRange must not extend into seed zone");
                }
                long seq = appendEvent(jdbc, SessionEventType.COMPACTION_CHECKPOINT, checkpointBody,
                        Map.of("from", String.valueOf(fromSeq), "to", String.valueOf(toSeq)));
                List<Long> visible = PersistedSessionStore.surfaceSeqs(jdbc, id.value());
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
                jdbc.update("DELETE FROM surface WHERE session_id=?", id.value());
                for (int i = 0; i < next.size(); i++) {
                    PersistedSessionStore.insertSurface(jdbc, id.value(), i, next.get(i));
                }
                jdbc.update("UPDATE sessions SET surface_explicit=1 WHERE id=?", id.value());
                PersistedSessionStore.putRegister(jdbc, id.value(), SurfaceEpoch.KEY,
                        SurfaceEpoch.next(PersistedSessionStore.getRegister(jdbc, id.value(), SurfaceEpoch.KEY)));
                return seq;
            });
        }

        @Override
        public List<SessionEvent> surface() {
            return store.tx(status -> {
                JdbcTemplate jdbc = store.jdbc();
                List<Long> seqs = PersistedSessionStore.surfaceSeqs(jdbc, id.value());
                List<SessionEvent> all = PersistedSessionStore.loadEvents(jdbc, id.value());
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
            store.tx(status -> {
                JdbcTemplate jdbc = store.jdbc();
                jdbc.update("UPDATE sessions SET enqueued=enqueued+1 WHERE id=?", id.value());
                long enq = jdbc.queryForObject("SELECT enqueued FROM sessions WHERE id=?", Long.class, id.value());
                jdbc.update(
                        "INSERT INTO inbox(session_id, message_id, body, source, priority, claim_id, expires_at, enq) "
                                + "VALUES (?,?,?,?,?,NULL,NULL,?)",
                        id.value(), mid, body, source == null ? null : source.orElse(null), p.name(), enq);
                return null;
            });
            return mid;
        }

        @Override
        public Optional<ClaimLease> claimNext() {
            return store.tx(status -> {
                JdbcTemplate jdbc = store.jdbc();
                long now = store.clock().instant().toEpochMilli();
                List<String> found = jdbc.query(
                        "SELECT message_id FROM inbox WHERE session_id=? AND (claim_id IS NULL OR expires_at < ?) "
                                + "ORDER BY CASE priority WHEN 'NOW' THEN 0 WHEN 'NEXT' THEN 1 ELSE 2 END, enq "
                                + "LIMIT 1",
                        (rs, i) -> rs.getString(1),
                        id.value(), now);
                if (found.isEmpty()) {
                    return Optional.empty();
                }
                String messageId = found.get(0);
                String claimId = UUID.randomUUID().toString();
                long expires = now + ClaimLease.DEFAULT_TTL.toMillis();
                jdbc.update("UPDATE inbox SET claim_id=?, expires_at=? WHERE session_id=? AND message_id=?",
                        claimId, expires, id.value(), messageId);
                return Optional.of(new ClaimLease(claimId, messageId, Instant.ofEpochMilli(expires)));
            });
        }

        @Override
        public Optional<InboxMessage> claimed(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            return store.tx(status -> loadClaimed(store.jdbc(), claimId));
        }

        @Override
        public void ack(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            store.tx(status -> {
                store.jdbc().update("DELETE FROM inbox WHERE session_id=? AND claim_id=?", id.value(), claimId);
                return null;
            });
        }

        @Override
        public void nack(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            store.tx(status -> {
                store.jdbc().update(
                        "UPDATE inbox SET claim_id=NULL, expires_at=NULL WHERE session_id=? AND claim_id=?",
                        id.value(), claimId);
                return null;
            });
        }

        @Override
        public long enqueued() {
            return store.tx(status -> store.jdbc().queryForObject(
                    "SELECT enqueued FROM sessions WHERE id=?", Long.class, id.value()));
        }

        @Override
        public boolean hasClaimableNow() {
            return store.tx(status -> {
                long now = store.clock().instant().toEpochMilli();
                return !store.jdbc().query(
                        "SELECT 1 FROM inbox WHERE session_id=? AND priority='NOW' "
                                + "AND (claim_id IS NULL OR expires_at < ?) LIMIT 1",
                        (rs, i) -> 1,
                        id.value(), now).isEmpty();
            });
        }

        private Optional<InboxMessage> loadClaimed(JdbcTemplate jdbc, String claimId) {
            List<InboxMessage> rows = jdbc.query(
                    "SELECT message_id, body, source, priority FROM inbox WHERE session_id=? AND claim_id=?",
                    (rs, i) -> new InboxMessage(
                            rs.getString(1),
                            rs.getString(2),
                            Optional.ofNullable(rs.getString(3)),
                            Priority.valueOf(rs.getString(4))),
                    id.value(), claimId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
    }

    private final class Ledger implements SessionLedger {
        @Override
        public long record(String syscallName, Usage usage, Map<String, String> attrs) {
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(syscallName, "syscallName");
            Map<String, String> a = attrs == null ? Map.of() : attrs;
            return store.tx(status -> {
                JdbcTemplate jdbc = store.jdbc();
                long seq = jdbc.queryForObject(
                        "SELECT COALESCE(MAX(seq),0)+1 FROM ledger WHERE session_id=?", Long.class, id.value());
                jdbc.update(
                        "INSERT INTO ledger(session_id, seq, at_millis, syscall_name, input_tokens, output_tokens, "
                                + "cache_read, cache_write, cost, attrs) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        id.value(),
                        seq,
                        store.clock().instant().toEpochMilli(),
                        syscallName,
                        usage.inputTokens(),
                        usage.outputTokens(),
                        usage.cacheReadTokens(),
                        usage.cacheWriteTokens(),
                        usage.cost().orElse(null),
                        AttrsJson.write(a));
                return seq;
            });
        }

        @Override
        public List<LedgerEntry> readAll() {
            return store.tx(status -> List.copyOf(store.jdbc().query(
                    "SELECT seq, at_millis, syscall_name, input_tokens, output_tokens, cache_read, cache_write, "
                            + "cost, attrs FROM ledger WHERE session_id=? ORDER BY seq",
                    (rs, i) -> {
                        String cost = rs.getString(8);
                        return new LedgerEntry(
                                rs.getLong(1),
                                Instant.ofEpochMilli(rs.getLong(2)),
                                rs.getString(3),
                                new Usage(rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7),
                                        Optional.ofNullable(cost)),
                                AttrsJson.read(rs.getString(9)));
                    },
                    id.value())));
        }
    }

    private final class Registers implements SessionRegisters {
        @Override
        public Optional<String> get(String key) {
            Objects.requireNonNull(key, "key");
            return store.tx(status -> Optional.ofNullable(
                    PersistedSessionStore.getRegister(store.jdbc(), id.value(), key)));
        }

        @Override
        public void put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (key.isBlank()) {
                throw new IllegalArgumentException("register key blank");
            }
            store.tx(status -> {
                PersistedSessionStore.putRegister(store.jdbc(), id.value(), key, value);
                return null;
            });
        }

        @Override
        public Map<String, String> snapshot() {
            return store.tx(status -> {
                LinkedHashMap<String, String> out = new LinkedHashMap<>();
                store.jdbc().query("SELECT k, v FROM registers WHERE session_id=? ORDER BY k", rs -> {
                    out.put(rs.getString(1), rs.getString(2));
                }, id.value());
                return Map.copyOf(out);
            });
        }
    }

    private final class Blobs implements ContentStore {
        @Override
        public String put(byte[] content) {
            Objects.requireNonNull(content, "content");
            String digest = sha256Hex(content);
            store.tx(status -> {
                store.jdbc().update("INSERT OR IGNORE INTO blobs(digest, bytes) VALUES (?,?)", digest, content);
                return null;
            });
            return digest;
        }

        @Override
        public Optional<byte[]> get(String digest) {
            if (digest == null) {
                return Optional.empty();
            }
            return store.tx(status -> {
                List<byte[]> rows = store.jdbc().query("SELECT bytes FROM blobs WHERE digest=?",
                        (rs, i) -> {
                            byte[] raw = rs.getBytes(1);
                            return raw == null ? null : Arrays.copyOf(raw, raw.length);
                        }, digest);
                return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0));
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
