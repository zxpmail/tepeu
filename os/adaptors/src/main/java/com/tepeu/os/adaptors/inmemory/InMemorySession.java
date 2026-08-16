package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.bus.Usage;
import com.tepeu.os.kernel.conformance.SessionConformance;
import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.session.ClaimLease;
import com.tepeu.os.kernel.session.InboxMessage;
import com.tepeu.os.kernel.session.LedgerEntry;
import com.tepeu.os.kernel.session.LogReplacePort;
import com.tepeu.os.kernel.session.Priority;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionEvent;
import com.tepeu.os.kernel.session.SessionEventType;
import com.tepeu.os.kernel.session.SessionId;
import com.tepeu.os.kernel.session.SessionInbox;
import com.tepeu.os.kernel.session.SessionLedger;
import com.tepeu.os.kernel.session.SessionLog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 单机内存会话 — 三 store（entries 日志 / Inbox+claim / ledger 账本）+ surface 替换。
 * 时钟可注入（TTL/超时可测试性）；租约过期在领取点惰性回收（ADR-016 第七轮：死租约可回收）。
 */
public final class InMemorySession implements Session {

    static final Duration LEASE_TTL = SessionConformance.LEASE_TTL;

    private final SessionId id;
    private final Namespace namespace;
    private final Principal owner;
    private final Optional<SessionId> parentId;
    private final Optional<String> forkFromEventId;
    private final Entries entries;
    private final Inbox inbox;
    private final Ledger ledger;

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        this(id, owner, namespace, parentId, Optional.empty(), Clock.systemUTC());
    }

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Clock clock) {
        this(id, owner, namespace, parentId, Optional.empty(), clock);
    }

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Optional<String> forkFromEventId, Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.parentId = parentId == null ? Optional.empty() : parentId;
        this.forkFromEventId = forkFromEventId == null ? Optional.empty() : forkFromEventId;
        Clock c = clock == null ? Clock.systemUTC() : clock;
        this.entries = new Entries(c);
        this.inbox = new Inbox(c);
        this.ledger = new Ledger(c);
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
    public LogReplacePort logReplace() {
        return entries;
    }

    /** 三 store 单机实现，各组件自锁（单机默认，勿当多副本范本）。 */

    /** entries：事实日志 + surface 替换投影（LogReplacePort 与 SessionLog 共享事件存储）。 */
    private static final class Entries implements SessionLog, LogReplacePort {
        private final Clock clock;
        private long logSeq = 0;
        private final List<SessionEvent> events = new ArrayList<>();
        /** null 表示 surface ≡ 全量日志；压缩后维护投影。 */
        private List<SessionEvent> surfaceOverride = null;

        Entries(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized long append(SessionEventType type, String body, Map<String, String> attrs) {
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
            long s = append(SessionEventType.COMPACTION_CHECKPOINT, checkpointBody,
                    Map.of("from", String.valueOf(fromSeq), "to", String.valueOf(toSeq)));
            SessionEvent checkpoint = events.get(events.size() - 1);
            List<SessionEvent> next = new ArrayList<>();
            boolean inserted = false;
            for (SessionEvent e : events) {
                if (e.seq() == s) {
                    // checkpoint 自身不按日志尾序进 surface，插入到被替换区间位置
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
            return s;
        }

        @Override
        public synchronized List<SessionEvent> surface() {
            return surfaceOverride == null ? List.copyOf(events) : surfaceOverride;
        }
    }

    /** Inbox：优先级领取 + TTL 租约 + 死租约惰性回收（第七轮）。 */
    private static final class Inbox implements SessionInbox {
        private final Clock clock;
        private final List<Pending> pending = new ArrayList<>();

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
            return mid;
        }

        @Override
        public synchronized Optional<ClaimLease> claimNext() {
            Instant now = clock.instant();
            // NOW > NEXT > LATER，同级按投入顺序（列表序）FIFO；
            // 过期租约的消息在此点惰性回收——视为可领取并改发新租约（第七轮）。
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
        public synchronized void ack(String claimId) {
            pending.removeIf(p -> claimId.equals(p.claimId()));
        }

        @Override
        public synchronized void nack(String claimId) {
            for (int i = 0; i < pending.size(); i++) {
                Pending p = pending.get(i);
                if (claimId.equals(p.claimId())) {
                    // 归还：保留原列表位，同级 FIFO 顺序不因 nack 改变
                    pending.set(i, new Pending(p.message(), null, null));
                    return;
                }
            }
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
        public synchronized long record(String syscallName, Usage usage) {
            Objects.requireNonNull(usage, "usage");
            long seq = ledgerEntries.size() + 1;
            ledgerEntries.add(new LedgerEntry(seq, clock.instant(), syscallName, usage));
            return seq;
        }

        @Override
        public synchronized List<LedgerEntry> readAll() {
            return List.copyOf(ledgerEntries);
        }
    }
}
