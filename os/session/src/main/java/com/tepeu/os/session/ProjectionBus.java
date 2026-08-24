package com.tepeu.os.session;

import com.tepeu.os.identity.SessionId;

import java.util.function.Consumer;

/**
 * 会话增量通知（⑤ 支撑）— 不是真相；真相在 {@link SessionLog}。
 * 慢消费者 bounded 背压：队列满则关闭该订阅（见 {@link memory.InMemoryProjectionBus}）。
 */
public interface ProjectionBus {

    /** @return 订阅 id，用于 {@link #unsubscribe} */
    String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer);

    void unsubscribe(String subscriptionId);

    void publish(SessionId sessionId, SessionEvent event);
}
