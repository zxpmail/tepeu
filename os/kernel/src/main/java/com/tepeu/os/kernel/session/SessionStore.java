package com.tepeu.os.kernel.session;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;

import java.util.Optional;

/**
 * 会话工厂/存取端口（内核必需端口之一）— 持久化细节在 ② 实现
 * （内存/SQLite 后端过同一 conformance；原 SessionRegistry 名已对齐底板 §3.1）。
 */
public interface SessionStore {
    Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId);

    Optional<Session> get(SessionId id);
}
