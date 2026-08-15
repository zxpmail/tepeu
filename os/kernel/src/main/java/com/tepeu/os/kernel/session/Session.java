package com.tepeu.os.kernel.session;

import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;

import java.util.Optional;

/**
 * 会话聚合端口：日志 + Inbox + 关系 + 替换。
 */
public interface Session {
    SessionId id();

    Namespace namespace();

    Principal owner();

    Optional<SessionId> parentId();

    Optional<String> forkFromEventId();

    SessionLog log();

    SessionInbox inbox();

    LogReplacePort logReplace();
}
