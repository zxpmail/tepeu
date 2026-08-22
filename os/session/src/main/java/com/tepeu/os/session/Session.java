package com.tepeu.os.session;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;

import java.util.Optional;

/**
 * 会话聚合端口：三 store（entries / registers / ledger）+ Inbox+claim + 关系 + surface 替换。
 * 「每个载荷恰好属于三者之一，没有第四个地方」（ADR-016 第五轮）。
 */
public interface Session {
    SessionId id();

    Namespace namespace();

    Principal owner();

    Optional<SessionId> parentId();

    Optional<String> forkFromEventId();

    SessionLog log();

    SessionInbox inbox();

    SessionLedger ledger();

    SessionRegisters registers();

    LogReplacePort logReplace();
}
