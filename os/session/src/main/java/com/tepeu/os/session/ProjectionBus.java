package com.tepeu.os.session;

import com.tepeu.os.identity.SessionId;

import java.util.function.Consumer;

/**
 * 会话增量通知（⑤ 支撑）— <b>不是真相</b>；真相在 {@link SessionLog}。
 * 慢消费者 bounded 背压：队列满则关闭该订阅（见 {@code memory.InMemoryProjectionBus}）。
 * 投影 ACL 过滤仍挂账；v1 仅推送。
 */
public interface ProjectionBus {

    /** @return 订阅 id，用于 {@link #unsubscribe} */
    String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer);

    void unsubscribe(String subscriptionId);

    void publish(SessionId sessionId, SessionEvent event);
}
