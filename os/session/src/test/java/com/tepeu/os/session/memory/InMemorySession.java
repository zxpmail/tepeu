package com.tepeu.os.session.memory;

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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 单机内存会话 — 测试夹具；语义与 sqlite 实现对齐。不进发行 jar。
 */
public final class InMemorySession implements Session {

    static final Duration LEASE_TTL = ClaimLease.DEFAULT_TTL;

    private final SessionId id;
    private final Namespace namespace;
    private final Principal owner;
    private final Optional<SessionId> parentId;
    private final Optional<String> forkFromEventId;
    private final long seedEndSeq;
    private final Entries entries;
    private final Inbox inbox;
    private final Ledger ledger;
    private final Registers registers;
    private final InMemoryContentStore blobs;

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        this(id, owner, namespace, parentId, Optional.empty(), Clock.systemUTC());
    }

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Clock clock) {
        this(id, owner, namespace, parentId, Optional.empty(), clock);
    }

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Optional<String> forkFromEventId, Clock clock) {
        this(id, owner, namespace, parentId, forkFromEventId, clock, List.of(), List.of(), 0L,
                Map.of(), Map.of());
    }

    InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Optional<String> forkFromEventId, Clock clock, List<SessionEvent> seedEvents,
            List<SessionEvent> seedSurface, long atSeq, Map<String, String> registerSeed,
            Map<String, byte[]> blobSeed) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.parentId = parentId == null ? Optional.empty() : parentId;
        this.forkFromEventId = forkFromEventId == null ? Optional.empty() : forkFromEventId;
        Clock c = clock == null ? Clock.systemUTC() : clock;
        this.registers = new Registers();
        this.entries = new Entries(c, seedEvents, seedSurface, atSeq, this.registers);
        this.seedEndSeq = this.entries.seedEndSeq();
        this.inbox = new Inbox(c);
        this.ledger = new Ledger(c);
        for (Map.Entry<String, String> e : registerSeed.entrySet()) {
            if ("loop.state".equals(e.getKey())) {
                continue;
            }
            this.registers.put(e.getKey(), e.getValue());
        }
        if (this.seedEndSeq > 0) {
            this.registers.put("fork.seedEnd", String.valueOf(this.seedEndSeq));
        }
        this.blobs = new InMemoryContentStore();
        for (Map.Entry<String, byte[]> e : blobSeed.entrySet()) {
            this.blobs.putKnown(e.getKey(), e.getValue());
        }
    }

    @Override
    public SessionId id() {
        return id;
    }

    @Override
    public Namespace namespace() {
        return namespace;
    }

    @Override
    public Principal owner() {
        return owner;
    }

    @Override
    public Optional<SessionId> parentId() {
        return parentId;
    }

    @Override
    public Optional<String> forkFromEventId() {
        return forkFromEventId;
    }

    @Override
    public Optional<Long> seedEndSeq() {
        return seedEndSeq > 0 ? Optional.of(seedEndSeq) : Optional.empty();
    }

    @Override
    public SessionLog log() {
        return entries;
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
        return entries;
    }

    @Override
    public ContentStore blobs() {
        return blobs;
    }

    InMemoryContentStore blobStore() {
        return blobs;
    }

    @Override
    public int recover() {
        int open = 0;
        for (SessionEvent event : entries.readAll()) {
            if (event.type() == SessionEventType.TOOL_CALL) {
                open++;
            } else if (event.type() == SessionEventType.TOOL_RESULT && open > 0) {
                open--;
            }
        }
        int unpaired = 0;
        for (int i = 0; i < open; i++) {
            entries.append(SessionEventType.TOOL_RESULT, "interrupted",
                    Map.of("errorCode", "INTERRUPTED"));
            unpaired++;
        }
        Optional<String> state = registers.get("loop.state");
        if (state.isPresent() && !"IDLE".equals(state.get())) {
            registers.put("loop.state", "IDLE");
        }
        return unpaired;
    }

    /** 三 store 单机实现，各组件自锁（单机默认，勿当多副本范本）。 */

    /** entries：事实日志 + surface 替换投影（LogReplacePort 与 SessionLog 共享事件存储）。 */
    private static final class Entries implements SessionLog, LogReplacePort {
        private final Clock clock;
        private final Registers registers;
        private long logSeq;
        private long seedEndSeq;
        private final List<SessionEvent> events = new ArrayList<>();
        /** null 表示 surface ≡ 全量日志中的模型可见事件；压缩后 / fork 后维护投影。 */
        private List<SessionEvent> surfaceOverride = null;

        Entries(Clock clock, List<SessionEvent> seedEvents, List<SessionEvent> seedSurface, long atSeq,
                Registers registers) {
            this.clock = clock;
            this.registers = Objects.requireNonNull(registers, "registers");
            if (seedEvents == null || seedEvents.isEmpty()) {
                this.logSeq = 0;
                this.seedEndSeq = 0;
                return;
            }
            if (atSeq <= 0) {
                throw new IllegalArgumentException("seed atSeq must be > 0 when events present");
            }
            for (SessionEvent event : seedEvents) {
                if (event.seq() > atSeq) {
                    continue;
                }
                events.add(event);
                this.logSeq = event.seq();
            }
            if (this.logSeq != atSeq) {
                throw new IllegalArgumentException("seed atSeq not in log: " + atSeq);
            }
            List<SessionEvent> surface = new ArrayList<>();
            if (seedSurface != null) {
                for (SessionEvent event : seedSurface) {
                    if (event.seq() <= atSeq && event.type() != SessionEventType.END_SEED) {
                        surface.add(event);
                    }
                }
            }
            this.surfaceOverride = List.copyOf(surface);
            long boundary = ++logSeq;
            events.add(new SessionEvent(boundary, SessionEventType.END_SEED, clock.instant(), "end-seed",
                    Map.of("from", String.valueOf(atSeq))));
            this.seedEndSeq = boundary;
        }

        long seedEndSeq() {
            return seedEndSeq;
        }

        @Override
        public synchronized long append(SessionEventType type, String body, Map<String, String> attrs) {
            Objects.requireNonNull(type, "type");
            if (type == SessionEventType.END_SEED) {
                throw new IllegalArgumentException("END_SEED is fork-only");
            }
            long s = ++logSeq;
            SessionEvent e = new SessionEvent(s, type, clock.instant(), body, attrs);
            events.add(e);
            if (surfaceOverride != null) {
                List<SessionEvent> next = new ArrayList<>(surfaceOverride);
                next.add(e);
                surfaceOverride = List.copyOf(next);
            }
            return s;
        }

        @Override
        public synchronized List<SessionEvent> readAll() {
            return List.copyOf(events);
        }

        @Override
        public synchronized Optional<SessionEvent> get(long seq) {
            return events.stream().filter(e -> e.seq() == seq).findFirst();
        }

        @Override
        public synchronized long replaceRange(long fromSeq, long toSeq, String checkpointBody) {
            if (fromSeq <= 0 || fromSeq > toSeq) {
                throw new IllegalArgumentException("invalid replace range: [" + fromSeq + "," + toSeq + "]");
            }
            if (seedEndSeq > 0 && fromSeq <= seedEndSeq) {
                throw new IllegalArgumentException("replaceRange must not extend into seed zone");
            }
            long s = append(SessionEventType.COMPACTION_CHECKPOINT, checkpointBody,
                    Map.of("from", String.valueOf(fromSeq), "to", String.valueOf(toSeq)));
            SessionEvent checkpoint = events.get(events.size() - 1);
            List<SessionEvent> visible = surfaceOverride == null ? modelVisible(events) : surfaceOverride;
            List<SessionEvent> next = new ArrayList<>();
            boolean inserted = false;
            for (SessionEvent e : visible) {
                if (e.seq() == s) {
                    continue;
                }
                if (e.seq() >= fromSeq && e.seq() <= toSeq) {
                    if (!inserted) {
                        next.add(checkpoint);
                        inserted = true;
                    }
                    continue;
                }
                next.add(e);
            }
            if (!inserted) {
                next.add(checkpoint);
            }
            surfaceOverride = List.copyOf(next);
            SurfaceEpoch.bump(registers);
            return s;
        }

        @Override
        public synchronized List<SessionEvent> surface() {
            if (surfaceOverride != null) {
                return surfaceOverride;
            }
            return List.copyOf(modelVisible(events));
        }

        private static List<SessionEvent> modelVisible(List<SessionEvent> source) {
            List<SessionEvent> out = new ArrayList<>();
            for (SessionEvent event : source) {
                if (event.type() != SessionEventType.END_SEED) {
                    out.add(event);
                }
            }
            return out;
        }
    }

    /** Inbox：优先级领取 + TTL 租约 + 死租约惰性回收（第七轮）。 */
    private static final class Inbox implements SessionInbox {
        private final Clock clock;
        private final List<Pending> pending = new ArrayList<>();
        private long enqueued;

        /** claimId == null 即可领取；非 null 时 expiresAt 过期即可回收重领（死租约）。 */
        private record Pending(InboxMessage message, String claimId, Instant expiresAt) {
            boolean claimable(Instant now) {
                return claimId == null || now.isAfter(expiresAt);
            }
        }

        Inbox(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized String enqueue(String body, Optional<String> source) {
            return enqueue(body, source, Priority.NEXT);
        }

        @Override
        public synchronized String enqueue(String body, Optional<String> source, Priority priority) {
            String mid = UUID.randomUUID().toString();
            pending.add(new Pending(new InboxMessage(mid, body, source, priority), null, null));
            enqueued++;
            return mid;
        }

        @Override
        public synchronized Optional<ClaimLease> claimNext() {
            Instant now = clock.instant();
            Pending best = null;
            for (Pending p : pending) {
                if (!p.claimable(now)) {
                    continue;
                }
                if (best == null || p.message().priority().ordinal() < best.message().priority().ordinal()) {
                    best = p;
                }
            }
            if (best == null) {
                return Optional.empty();
            }
            String claimId = UUID.randomUUID().toString();
            Instant expiresAt = now.plus(LEASE_TTL);
            pending.set(pending.indexOf(best), new Pending(best.message(), claimId, expiresAt));
            return Optional.of(new ClaimLease(claimId, best.message().id(), expiresAt));
        }

        @Override
        public synchronized Optional<InboxMessage> claimed(String claimId) {
            Objects.requireNonNull(claimId, "claimId");
            for (Pending p : pending) {
                if (claimId.equals(p.claimId())) {
                    return Optional.of(p.message());
                }
            }
            return Optional.empty();
        }

        @Override
        public synchronized void ack(String claimId) {
            pending.removeIf(p -> claimId.equals(p.claimId()));
        }

        @Override
        public synchronized void nack(String claimId) {
            for (int i = 0; i < pending.size(); i++) {
                Pending p = pending.get(i);
                if (claimId.equals(p.claimId())) {
                    pending.set(i, new Pending(p.message(), null, null));
                    return;
                }
            }
        }

        @Override
        public synchronized long enqueued() {
            return enqueued;
        }

        @Override
        public synchronized boolean hasClaimableNow() {
            Instant now = clock.instant();
            for (Pending p : pending) {
                if (p.claimable(now) && p.message().priority() == Priority.NOW) {
                    return true;
                }
            }
            return false;
        }
    }

    /** ledger：append-only 用量账本。 */
    private static final class Ledger implements SessionLedger {
        private final Clock clock;
        private final List<LedgerEntry> ledgerEntries = new ArrayList<>();

        Ledger(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized long record(String syscallName, Usage usage, Map<String, String> attrs) {
            Objects.requireNonNull(usage, "usage");
            long seq = ledgerEntries.size() + 1;
            ledgerEntries.add(new LedgerEntry(seq, clock.instant(), syscallName, usage, attrs));
            return seq;
        }

        @Override
        public synchronized List<LedgerEntry> readAll() {
            return List.copyOf(ledgerEntries);
        }
    }

    /** registers：覆盖写；缺键 = empty。 */
    private static final class Registers implements SessionRegisters {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override
        public synchronized Optional<String> get(String key) {
            Objects.requireNonNull(key, "key");
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public synchronized void put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (key.isBlank()) {
                throw new IllegalArgumentException("register key blank");
            }
            values.put(key, value);
        }

        @Override
        public synchronized Map<String, String> snapshot() {
            return Map.copyOf(values);
        }
    }
}
