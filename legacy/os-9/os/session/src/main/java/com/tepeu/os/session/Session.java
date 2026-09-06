package com.tepeu.os.session;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.SessionId;

import java.util.Optional;

/**
 * 一次对话的把手。谁、在哪、从哪分出来，以及往哪本账上写。
 * <p>
 * 对话事实进 {@link #log()}，用量进 {@link #ledger()}，运行中小状态进 {@link #registers()}。
 * 一件事只进其中一个，不要另开地方。人点批准不走这里，走 {@link AuditSink}。
 */
public interface Session {
    /** 这是哪一次对话。 */
    SessionId id();

    /** 落在哪块工作区（租户位现在是空的）。 */
    Namespace namespace();

    /** 谁开的这会话。 */
    Principal owner();

    /** 从哪次会话派生的；没有就是全新开的。 */
    Optional<SessionId> parentId();

    /** fork 时钉在源日志哪条上；没有就是没 fork。 */
    Optional<String> forkFromEventId();

    /**
     * fork 带来的种子接到哪一号。没 fork 就是空的。
     * 压缩改模型看见的那段时，不准切到这个号或更早。
     */
    Optional<Long> seedEndSeq();

    /** 对话流水：人说了什么、模型回了什么、工具进出。只追加，压缩也不删。 */
    SessionLog log();

    /** 人丢进来的下一句话。领取带租约，用完 ack。不是调度器。 */
    SessionInbox inbox();

    /** token / 花费流水。Metering 只来这里看数，自己不喊停。 */
    SessionLedger ledger();

    /** 运行中小黑板，比如 loop 现在是不是 IDLE。不是对话正文。 */
    SessionRegisters registers();

    /** 压缩：底下流水不动，换模型看见的那一截。 */
    LogReplacePort logReplace();

    /** 大附件落盘，事件里只留指针。 */
    ContentStore blobs();

    /**
     * 崩溃后收拾：有工具呼叫还没结果的，补一条「打断了」。
     * 旧日志不动。loop 若停在非空闲，拨回空闲。返回补了几条。
     */
    int recover();
}
