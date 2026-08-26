package com.tepeu.os.session;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;

import java.util.Optional;

/**
 * 会话工厂 / 存取端口（内核必需端口之一）。
 * 持久化细节在 persist 组件（发行 SQLite 插头）与测试源 memory 夹具；本接口不含 JDBC。
 * 不是 Registry 单例；多副本 fencing 未做（单写者发行）。
 */
public interface SessionStore {
    Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId);

    Optional<Session> get(SessionId id);

    /**
     * 从 {@code source} 在 {@code atSeq} 处 fork：种子事件保留原 seq，写入 END_SEED 后续接，
     * 携带 surface 记账。{@code atSeq} 必须是源日志已有事件（空日志用 0）。
     */
    Session fork(SessionId source, long atSeq);
}
