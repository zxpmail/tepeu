package com.tepeu.os.session;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;

import java.util.Optional;

/**
 * 会话工厂/存取端口（内核必需端口之一）— 持久化细节在 ② 实现
 * （内存/SQLite 后端过同一 conformance；原 SessionRegistry 名已对齐底板 §3.1）。
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
