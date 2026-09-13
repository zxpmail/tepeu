package com.tepeu.session;

import com.tepeu.identity.Principal;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;

/**
 * 一次对话的把手。事实进 {@link #log()}，用量进 {@link #ledger()}，
 * 运行态进 {@link #registers()}。批准/拒绝走 {@link #audit()}，不进日志。
 * @author zxpma
 */
public interface Session {

    SessionId id();

    Principal owner();

    WorkspaceId workspace();

    SessionLog log();

    SessionInbox inbox();

    SessionLedger ledger();

    SessionRegisters registers();

    AuditSink audit();
}
