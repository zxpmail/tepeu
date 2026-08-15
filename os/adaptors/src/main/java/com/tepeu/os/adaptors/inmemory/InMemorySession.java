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
 */
public final class InMemorySession implements Session {

    private final SessionId id;
    private final Namespace namespace;
    private final Principal owner;
    private final Optional<SessionId> parentId;
    private final LogAndInbox core = new LogAndInbox();

    public InMemorySession(SessionId id, Principal owner, Namespace namespace, Optional<SessionId> parentId) {
        this.id = id;
        this.owner = owner;
        this.namespace = namespace;
        this.parentId = parentId == null ? Optional.empty() : parentId;
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
        private final Map<String, InboxMessage> claimed = new ConcurrentHashMap<>();
        /** null 表示 surface ≡ 全量日志；压缩后维护投影。 */
        private List<SessionEvent> surfaceOverride = null;

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
            InboxMessage msg = queue.poll();
            if (msg == null) {
                return Optional.empty();
            }
            String claimId = UUID.randomUUID().toString();
            claimed.put(claimId, msg);
            return Optional.of(new ClaimLease(claimId, msg.id(), Instant.now().plusSeconds(300)));
        }

        @Override
        public void ack(String claimId) {
            claimed.remove(claimId);
        }

        @Override
        public void nack(String claimId) {
            InboxMessage msg = claimed.remove(claimId);
            if (msg != null) {
                queue.offer(msg);
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
