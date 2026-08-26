package com.tepeu.os.session;

import com.tepeu.os.identity.SessionId;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * {@link ProjectionBus} 的本机默认插头 — 进程内订阅/推送。
 * 不是契约本身，不是消息真相，不是记忆平面，不是必装中间件。
 * 慢消费者 bounded 背压：队列满则关闭该订阅，并记运维诊断（JDK {@link Logger}，不是 slf4j、不进 entries）。
 * 每条诊断带 {@code component=session class=LocalProjectionBus}，便于和别的插头区分。
 * 消费者异常同样可见，且不阻断同会话其他订阅。
 * 其他组件不需要实现本类；MQ 升版另写插头即可。{@code InMemory*} 留给测试夹具。
 */
public final class LocalProjectionBus implements ProjectionBus {

    public static final int DEFAULT_CAPACITY = 64;
    private static final String COMPONENT = "session";
    private static final String CLASS_NAME = LocalProjectionBus.class.getSimpleName();

    private final int capacity;
    private final Logger log;
    private final Consumer<String> diagnostics;
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public LocalProjectionBus() {
        this(DEFAULT_CAPACITY);
    }

    public LocalProjectionBus(int capacity) {
        this(capacity, System.getLogger(LocalProjectionBus.class.getName()), null);
    }

    /** 测试/宿主可再接一条诊断槽；生产默认仍走 JDK Logger。 */
    public LocalProjectionBus(int capacity, Consumer<String> diagnostics) {
        this(capacity, System.getLogger(LocalProjectionBus.class.getName()), diagnostics);
    }

    LocalProjectionBus(int capacity, Logger log, Consumer<String> diagnostics) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity < 1");
        }
        this.capacity = capacity;
        this.log = Objects.requireNonNull(log, "log");
        this.diagnostics = diagnostics;
    }

    @Override
    public String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(consumer, "consumer");
        String id = UUID.randomUUID().toString();
        subscriptions.put(id, new Subscription(sessionId, consumer));
        log.log(Level.DEBUG, () -> locate("subscribe id=" + id + " session=" + sessionId.value()));
        return id;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        if (subscriptionId != null) {
            subscriptions.remove(subscriptionId);
            log.log(Level.DEBUG, () -> locate("unsubscribe id=" + subscriptionId));
        }
    }

    @Override
    public void publish(SessionId sessionId, SessionEvent event) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(event, "event");
        List<String> drop = new ArrayList<>();
        for (var entry : subscriptions.entrySet()) {
            Subscription sub = entry.getValue();
            if (!sub.sessionId.equals(sessionId)) {
                continue;
            }
            if (sub.pending >= capacity) {
                drop.add(entry.getKey());
                warn("drop subscription=" + entry.getKey()
                        + " session=" + sessionId.value()
                        + " seq=" + event.seq()
                        + " capacity=" + capacity, null);
                continue;
            }
            sub.pending++;
            try {
                sub.consumer.accept(event);
            } catch (RuntimeException e) {
                warn("consumer failed subscription=" + entry.getKey()
                        + " session=" + sessionId.value()
                        + " seq=" + event.seq(), e);
            } finally {
                sub.pending--;
            }
        }
        for (String id : drop) {
            subscriptions.remove(id);
        }
    }

    private void warn(String message, Throwable error) {
        String located = locate(message);
        if (diagnostics != null) {
            diagnostics.accept(located);
            return;
        }
        if (error == null) {
            log.log(Level.WARNING, located);
        } else {
            log.log(Level.WARNING, located, error);
        }
    }

    private static String locate(String message) {
        return "component=" + COMPONENT + " class=" + CLASS_NAME + " " + message;
    }

    private static final class Subscription {
        final SessionId sessionId;
        final Consumer<SessionEvent> consumer;
        int pending;

        Subscription(SessionId sessionId, Consumer<SessionEvent> consumer) {
            this.sessionId = sessionId;
            this.consumer = consumer;
        }
    }
}
