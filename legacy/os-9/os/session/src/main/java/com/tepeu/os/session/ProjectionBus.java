package com.tepeu.os.session;

import com.tepeu.os.identity.SessionId;

import java.util.function.Consumer;

/**
 * 给旁边听的喇叭：有新事件时推一把。
 * <p>
 * 对话真相仍在 {@link SessionLog}。完成、审批、对账都别问这里。
 * 可以不接、可以不订。本机默认 {@link com.tepeu.os.session.local.LocalProjectionBus}。
 * 换 MQ 还是这个口、另写实现；现在不引 broker，也不把本机插头写成第二份契约。
 * {@code subscribe} 的 Consumer 是进程内回调。跨进程听众不走这个方法。
 * 听的人太慢被丢掉、或回调炸了，要让运维看得见，但不要写进对话流水。
 */
public interface ProjectionBus {

    /** 订某次会话（本进程回调）。返回订阅号，退订时用。 */
    String subscribe(SessionId sessionId, Consumer<SessionEvent> consumer);

    /** 按订阅号退订。 */
    void unsubscribe(String subscriptionId);

    /** 推一条。没人订就丢在地上，不当事故。 */
    void publish(SessionId sessionId, SessionEvent event);
}
