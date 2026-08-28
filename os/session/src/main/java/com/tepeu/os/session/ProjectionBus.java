package com.tepeu.os.session;

import com.tepeu.os.identity.SessionId;

import java.util.function.Consumer;

/**
 * 会话增量通知端口（⑤/② 支撑）— <b>不是真相</b>；真相在 {@link SessionLog}。
 * <p>
 * 契约是本接口。本机默认实现是 {@link LocalProjectionBus}，不是第二份契约。
 * 其他组件<b>可以实现、也可以不实现、可以不订阅</b>；不是内核必需端口。
 * Redis/NATS/MQ 是同一接口的另一实现，升版再落；本骨架不引入 broker。
 * 实现须让慢消费者 drop 与消费者失败<b>可见</b>（运维诊断，不进 entries / AuditSink）。
 * 投影 ACL 过滤仍挂账；v1 仅推送。
 */
public interface ProjectionBus {

    /** @return 订阅 id，用于 {@link #unsubscribe} */
    String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer);

    void unsubscribe(String subscriptionId);

    void publish(SessionId sessionId, SessionEvent event);
}
