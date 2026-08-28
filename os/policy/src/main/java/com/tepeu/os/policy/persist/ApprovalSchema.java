package com.tepeu.os.policy.persist;

/** 审批库 DDL。persist 只负责执行。 */
final class ApprovalSchema {

    static final String DDL = """
            CREATE TABLE IF NOT EXISTS approvals (
              approval_id TEXT PRIMARY KEY,
              session_id TEXT NOT NULL,
              syscall_name TEXT NOT NULL,
              args_digest TEXT NOT NULL,
              asked_at INTEGER NOT NULL,
              decided_at INTEGER,
              allow_flag INTEGER,
              decided_by TEXT,
              consumed INTEGER NOT NULL DEFAULT 0
            );
            """;

    private ApprovalSchema() {
    }
}
