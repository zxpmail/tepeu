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

    /** 种子边界 seq（END_SEED）；非 fork 则 empty。自身写入的 replaceRange 不得 ≤ 此值。 */
    Optional<Long> seedEndSeq();

    SessionLog log();

    SessionInbox inbox();

    SessionLedger ledger();

    SessionRegisters registers();

    LogReplacePort logReplace();

    ContentStore blobs();

    /**
     * 崩溃补闭合：未配对 TOOL_CALL 补合成 TOOL_RESULT（INTERRUPTED），不截断日志。
     * 若 registers 有 {@code loop.state} 且非 IDLE，点查写回 IDLE。返回补了几条 RESULT。
     */
    int recover();
}
