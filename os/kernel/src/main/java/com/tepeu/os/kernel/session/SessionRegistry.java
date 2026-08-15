package com.tepeu.os.kernel.session;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;

import java.util.Optional;

/**
 * 会话工厂 / 注册表端口（持久化细节在 SessionStore 适配器）。
 */
public interface SessionRegistry {
    Session create(Principal owner, Namespace namespace, Optional<SessionId> parentId);

    Optional<Session> get(SessionId id);
}
