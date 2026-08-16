package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.session.ClaimLease;
import com.tepeu.os.kernel.session.InboxMessage;
import com.tepeu.os.kernel.session.LogReplacePort;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionEvent;
import com.tepeu.os.kernel.session.SessionEventType;
import com.tepeu.os.kernel.session.SessionId;
import com.tepeu.os.kernel.session.SessionInbox;
import com.tepeu.os.kernel.session.SessionLog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单机内存会话 — 日志 + Inbox + 简单 surface 替换。
 * 时钟可注入（TTL/超时可测试性）；租约过期在领取点惰性回收（ADR-016 第七轮：死租约可回收）。
 */
public final class InMemorySession implements Session {

    static final Duration LEASE_TTL = Duration.ofSeconds(300);

    private final SessionId id;
    private final Namespace namespace;
    private final Principal owner;
    private final Optional<SessionId> parentId;
    private final Clock clock;
    private final LogAndInbox core;

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        this(id, owner, namespace, parentId, Clock.systemUTC());
    }

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId,
            Clock clock) {
        this.id = id;
        this.owner = owner;
        this.namespace = namespace;
        this.parentId = parentId == null ? Optional.empty() : parentId;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.core = new LogAndInbox(this.clock);
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
        return Optional.empty();
    }

    @Override
    public SessionLog log() {
        return core;
    }

    @Override
    public SessionInbox inbox() {
        return core;
    }

    @Override
    public LogReplacePort logReplace() {
        return core;
    }

    private static final class LogAndInbox implements SessionLog, SessionInbox, LogReplacePort {
        private final AtomicLong seq = new AtomicLong(0);
        private final List<SessionEvent> events = new ArrayList<>();
        private final ConcurrentLinkedQueue<InboxMessage> queue = new ConcurrentLinkedQueue<>();
        private final Map<String, Claimed> claimed = new ConcurrentHashMap<>();

        private record Claimed(InboxMessage message, Instant expiresAt) {
        }
        /** null 表示 surface ≡ 全量日志；压缩后维护投影。 */
        private List<SessionEvent> surfaceOverride = null;
        private final Clock clock;

        LogAndInbox(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized long append(SessionEventType type, String body, Map<String, String> attrs) {
            long s = seq.incrementAndGet();
            SessionEvent e = new SessionEvent(s, type, Instant.now(), body, attrs);
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
        public synchronized Optional<SessionEvent> get(long sequence) {
            return events.stream().filter(e -> e.seq() == sequence).findFirst();
        }

        @Override
        public String enqueue(String body, Optional<String> source) {
            String mid = UUID.randomUUID().toString();
            queue.add(new InboxMessage(mid, body, source));
            return mid;
        }

        @Override
        public Optional<ClaimLease> claimNext() {
            reclaimExpiredLeases();
            InboxMessage msg = queue.poll();
            if (msg == null) {
                return Optional.empty();
            }
            String claimId = UUID.randomUUID().toString();
            Instant expiresAt = clock.instant().plus(LEASE_TTL);
            claimed.put(claimId, new Claimed(msg, expiresAt));
            return Optional.of(new ClaimLease(claimId, msg.id(), expiresAt));
        }

        /**
         * 惰性回收过期租约：持有者异常消失（进程存活）后消息不再永久卡死（ADR-016 第七轮）。
         */
        private void reclaimExpiredLeases() {
            Instant now = clock.instant();
            claimed.entrySet().removeIf(entry -> {
                if (now.isAfter(entry.getValue().expiresAt())) {
                    queue.offer(entry.getValue().message());
                    return true;
                }
                return false;
            });
        }

        @Override
        public void ack(String claimId) {
            claimed.remove(claimId);
        }

        @Override
        public void nack(String claimId) {
            Claimed c = claimed.remove(claimId);
            if (c != null) {
                queue.offer(c.message());
            }
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
}
