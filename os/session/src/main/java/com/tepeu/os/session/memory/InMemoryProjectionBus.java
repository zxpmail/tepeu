package com.tepeu.os.session.memory;

import com.tepeu.os.identity.SessionId;
import com.tepeu.os.session.ProjectionBus;
import com.tepeu.os.session.SessionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 单机 bounded 投影总线 — conformance / 本机 UI 客户端用。不是消息真相。
 */
public final class InMemoryProjectionBus implements ProjectionBus {

    public static final int DEFAULT_CAPACITY = 64;

    private final int capacity;
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public InMemoryProjectionBus() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryProjectionBus(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity < 1");
        }
        this.capacity = capacity;
    }

    @Override
    public String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(consumer, "consumer");
        String id = UUID.randomUUID().toString();
        subscriptions.put(id, new Subscription(sessionId, consumer));
        return id;
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        if (subscriptionId != null) {
            subscriptions.remove(subscriptionId);
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
                continue;
            }
            sub.pending++;
            try {
                sub.consumer.accept(event);
            } finally {
                sub.pending--;
            }
        }
        for (String id : drop) {
            subscriptions.remove(id);
        }
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
