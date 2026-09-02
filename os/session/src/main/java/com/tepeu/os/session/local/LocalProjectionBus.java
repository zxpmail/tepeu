package com.tepeu.os.session.local;

import com.tepeu.os.session.*;

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
 * {@link ProjectionBus} 的本机插头 — 进程内同步直推。
 * <p>
 * 不是契约。换 MQ / Redis 另写一个实现，走同一个接口；不要改本类、不要在这里引 broker。
 * {@code subscribe(Consumer)} 只服务本进程回调。另一进程的 UI 听 broker / SSE，不进这个 Consumer。
 * 没有队列。{@code pending} 数的是「这条回调还没返回」（重入或并发叠在一起），不是积压条数。
 * 到顶就摘掉这个订阅，记一条运维诊断，不进 entries，不加 slf4j。
 * 回调太慢会堵住这次 {@code publish}，同会话后面的订户也要等。
 * 一个订户炸了，别的还推。没人订就丢在地上。
 * {@code InMemory*} 留给测试夹具。
 */
public final class LocalProjectionBus implements ProjectionBus {

    /** 同一订阅上允许叠着还没返回的 {@code accept} 次数。不是队列长度。 */
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

    /** 本进程订一次会话。订阅号只在这张表里有效，带不出进程。 */
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

    /** 同步推给该会话的订户。pending 到顶则摘订阅，不排队。 */
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
        /** 正在跑的 accept 层数。嵌套 publish 会抬高。 */
        int pending;

        Subscription(SessionId sessionId, Consumer<SessionEvent> consumer) {
            this.sessionId = sessionId;
            this.consumer = consumer;
        }
    }
}
