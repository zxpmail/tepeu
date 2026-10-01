package com.tepeu.session.persist;

import com.tepeu.identity.Principal;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.Persist;
import com.tepeu.persist.PersistRecord;
import com.tepeu.session.AuditRecord;
import com.tepeu.session.AuditSink;
import com.tepeu.session.ClaimLease;
import com.tepeu.session.InboxMessage;
import com.tepeu.session.LedgerEntry;
import com.tepeu.session.Priority;
import com.tepeu.session.Session;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionInbox;
import com.tepeu.session.SessionLedger;
import com.tepeu.session.SessionLog;
import com.tepeu.session.SessionRegisters;
import com.tepeu.syscall.Usage;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * 一会话行。五本账的 space 由这里命名。
 * <p>单写者前提：五本账 seq 用 {@code list().size()+1} 分配，收件箱领取用全表扫描置章，
 * 都是非原子读改写。只许单进程单写者使用；loop 线程化之前必须收口。
 */
final class PersistedSession implements Session {

    private final Persist persist;
    private final SessionId id;
    private final Principal owner;
    private final WorkspaceId workspace;
    private final Clock clock;
    private final SessionLog log = new Log();
    private final SessionInbox inbox = new Inbox();
    private final SessionLedger ledger = new Ledger();
    private final SessionRegisters registers = new Registers();
    private final AuditSink audit = new Audit();

    PersistedSession(
            Persist persist,
            SessionId id,
            Principal owner,
            WorkspaceId workspace,
            Clock clock) {
        this.persist = persist;
        this.id = id;
        this.owner = owner;
        this.workspace = workspace;
        this.clock = clock;
    }

    static String space(SessionId id, String name) {
        return "session/" + id.value() + "/" + name;
    }

    @Override
    public SessionId id() {
        return id;
    }

    @Override
    public Principal owner() {
        return owner;
    }

    @Override
    public WorkspaceId workspace() {
        return workspace;
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
    public AuditSink audit() {
        return audit;
    }

    private String space(String name) {
        return space(id, name);
    }

    private long nextSeq(String name) {
        return persist.list(space(name)).size() + 1L;
    }

    /** 序号簿共用骨架：盖 at 章、按 seq 键追加，返回 seq。 */
    private long appendDated(String name, Map<String, String> fields) {
        long seq = nextSeq(name);
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("at", clock.instant().toString());
        persist.append(space(name), new PersistRecord(String.valueOf(seq), all));
        return seq;
    }

    private <T> List<T> readBook(String name, Function<PersistRecord, T> parse) {
        return persist.list(space(name)).stream().map(parse).toList();
    }

    private final class Log implements SessionLog {

        private static final String ATTR_PREFIX = "a.";

        @Override
        public long append(SessionEventType type, String body, Map<String, String> attrs) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(body, "body");
            Map<String, String> copy = attrs == null ? Map.of() : Map.copyOf(attrs);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("type", type.name());
            fields.put("body", body);
            for (Map.Entry<String, String> entry : copy.entrySet()) {
                if (entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("event attr key blank");
                }
                fields.put(ATTR_PREFIX + entry.getKey(), entry.getValue());
            }
            return appendDated("events", fields);
        }

        @Override
        public List<SessionEvent> readAll() {
            return readBook("events", Log::toEvent);
        }

        @Override
        public Optional<SessionEvent> get(long seq) {
            return persist.get(space("events"), String.valueOf(seq)).map(Log::toEvent);
        }

        private static SessionEvent toEvent(PersistRecord record) {
            Map<String, String> fields = record.fields();
            Map<String, String> attrs = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey().startsWith(ATTR_PREFIX)) {
                    attrs.put(entry.getKey().substring(ATTR_PREFIX.length()), entry.getValue());
                }
            }
            return new SessionEvent(
                    Long.parseLong(record.key()),
                    SessionEventType.valueOf(fields.get("type")),
                    Instant.parse(fields.get("at")),
                    fields.get("body"),
                    attrs);
        }
    }

    private final class Registers implements SessionRegisters {

        @Override
        public Optional<String> get(String key) {
            requireKey(key);
            return persist.get(space("registers"), key).map(row -> row.fields().get("value"));
        }

        @Override
        public void put(String key, String value) {
            requireKey(key);
            Objects.requireNonNull(value, "value");
            persist.put(space("registers"), new PersistRecord(key, Map.of("value", value)));
        }

        @Override
        public Map<String, String> snapshot() {
            Map<String, String> out = new HashMap<>();
            for (PersistRecord row : persist.list(space("registers"))) {
                out.put(row.key(), row.fields().get("value"));
            }
            return Map.copyOf(out);
        }

        private static void requireKey(String key) {
            Objects.requireNonNull(key, "key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("register key blank");
            }
        }
    }

    private final class Ledger implements SessionLedger {

        @Override
        public long record(String syscallName, Usage usage) {
            Objects.requireNonNull(syscallName, "syscallName");
            if (syscallName.isBlank()) {
                throw new IllegalArgumentException("syscallName blank");
            }
            Objects.requireNonNull(usage, "usage");
            return appendDated("ledger", Map.of(
                    "syscallName", syscallName,
                    "inputTokens", Long.toString(usage.inputTokens()),
                    "outputTokens", Long.toString(usage.outputTokens()),
                    "cost", usage.cost().orElse("")));
        }

        @Override
        public List<LedgerEntry> readAll() {
            return readBook("ledger", Ledger::toEntry);
        }

        private static LedgerEntry toEntry(PersistRecord record) {
            Map<String, String> fields = record.fields();
            String cost = fields.getOrDefault("cost", "");
            Usage usage = new Usage(
                    Long.parseLong(fields.get("inputTokens")),
                    Long.parseLong(fields.get("outputTokens")),
                    cost.isBlank() ? Optional.empty() : Optional.of(cost));
            return new LedgerEntry(
                    Long.parseLong(record.key()),
                    Instant.parse(fields.get("at")),
                    fields.get("syscallName"),
                    usage);
        }
    }

    private final class Inbox implements SessionInbox {

        @Override
        public String enqueue(String body) {
            return enqueue(body, Priority.NEXT);
        }

        @Override
        public String enqueue(String body, Priority priority) {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(priority, "priority");
            String messageId = UUID.randomUUID().toString();
            persist.append(space("inbox"), new PersistRecord(messageId, Map.of(
                    "body", body,
                    "priority", priority.name(),
                    "claimId", "",
                    "expiresAt", "",
                    "acked", "")));
            return messageId;
        }

        @Override
        public Optional<ClaimLease> claimNext() {
            Instant now = clock.instant();
            PersistRecord pick = null;
            Priority best = null;
            for (PersistRecord row : persist.list(space("inbox"))) {
                if (!claimable(row, now)) {
                    continue;
                }
                Priority priority = Priority.valueOf(row.fields().get("priority"));
                if (pick == null || priority.ordinal() < best.ordinal()) {
                    pick = row;
                    best = priority;
                }
            }
            if (pick == null) {
                return Optional.empty();
            }
            String claimId = UUID.randomUUID().toString();
            Instant expiresAt = now.plus(ClaimLease.DEFAULT_TTL);
            persist.put(space("inbox"), claimedRow(pick, claimId, expiresAt));
            return Optional.of(new ClaimLease(claimId, pick.key(), expiresAt));
        }

        @Override
        public Optional<InboxMessage> claimed(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            return persist.list(space("inbox")).stream()
                    .filter(row -> claimId.equals(row.fields().get("claimId")))
                    .filter(row -> !acked(row))
                    .findFirst()
                    .map(Inbox::toMessage);
        }

        @Override
        public void ack(String claimId) {
            PersistRecord row = requireClaim(claimId);
            Map<String, String> fields = new LinkedHashMap<>(row.fields());
            fields.put("acked", "true");
            persist.put(space("inbox"), new PersistRecord(row.key(), fields));
        }

        @Override
        public void nack(String claimId) {
            PersistRecord row = requireClaim(claimId);
            persist.put(space("inbox"), claimedRow(row, "", Instant.EPOCH));
        }

        private PersistRecord requireClaim(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            return persist.list(space("inbox")).stream()
                    .filter(row -> claimId.equals(row.fields().get("claimId")))
                    .filter(row -> !acked(row))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("inbox claim missing"));
        }

        private static PersistRecord claimedRow(PersistRecord row, String claimId, Instant expiresAt) {
            Map<String, String> fields = new LinkedHashMap<>(row.fields());
            fields.put("claimId", claimId);
            fields.put("expiresAt", claimId.isEmpty() ? "" : expiresAt.toString());
            fields.put("acked", "");
            return new PersistRecord(row.key(), fields);
        }

        private static boolean claimable(PersistRecord row, Instant now) {
            if (acked(row)) {
                return false;
            }
            String claimId = row.fields().getOrDefault("claimId", "");
            if (claimId.isBlank()) {
                return true;
            }
            String expiresAt = row.fields().getOrDefault("expiresAt", "");
            return !expiresAt.isBlank() && Instant.parse(expiresAt).isBefore(now);
        }

        private static boolean acked(PersistRecord row) {
            return "true".equals(row.fields().get("acked"));
        }

        private static InboxMessage toMessage(PersistRecord row) {
            return new InboxMessage(
                    row.key(),
                    row.fields().get("body"),
                    Priority.valueOf(row.fields().get("priority")));
        }
    }

    private final class Audit implements AuditSink {

        @Override
        public long record(String actor, String action, String detail) {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(detail, "detail");
            return appendDated("audit", Map.of(
                    "actor", actor,
                    "action", action,
                    "detail", detail));
        }

        @Override
        public List<AuditRecord> readAll() {
            return readBook("audit", Audit::toRecord);
        }

        private static AuditRecord toRecord(PersistRecord record) {
            Map<String, String> fields = record.fields();
            return new AuditRecord(
                    Long.parseLong(record.key()),
                    Instant.parse(fields.get("at")),
                    fields.get("actor"),
                    fields.get("action"),
                    fields.get("detail"));
        }
    }
}
