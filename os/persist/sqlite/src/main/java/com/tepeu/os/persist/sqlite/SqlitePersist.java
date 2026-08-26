package com.tepeu.os.persist.sqlite;

import com.tepeu.os.persist.Persist;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.SessionStore;

import java.nio.file.Path;
import java.util.Objects;

/**
 * SQLite 引擎插头。会话 {@code kernel.sqlite} 与审批 {@code approvals.sqlite} 分库，
 * 共用 {@link SqliteDb} JDBC 栈。compose 只选本类，不直接 new 两套 store 当换库点。
 */
public final class SqlitePersist implements Persist {

    public static final String KERNEL_FILE = "kernel.sqlite";
    public static final String APPROVALS_FILE = "approvals.sqlite";

    private final SqliteSessionStore sessions;
    private final SqliteApprovalStore approvals;

    private SqlitePersist(SqliteSessionStore sessions, SqliteApprovalStore approvals) {
        this.sessions = sessions;
        this.approvals = approvals;
    }

    public static SqlitePersist file(Path dir) {
        Objects.requireNonNull(dir, "dir");
        SqliteSessionStore sessions = new SqliteSessionStore(dir.resolve(KERNEL_FILE));
        try {
            SqliteApprovalStore approvals = new SqliteApprovalStore(dir.resolve(APPROVALS_FILE));
            return new SqlitePersist(sessions, approvals);
        } catch (RuntimeException e) {
            try {
                sessions.close();
            } catch (RuntimeException ignored) {
                // original in flight
            }
            throw e;
        }
    }

    @Override
    public SessionStore sessions() {
        return sessions;
    }

    @Override
    public ApprovalStore approvals() {
        return approvals;
    }

    @Override
    public AuditSink audit() {
        return sessions.audit();
    }

    @Override
    public void close() {
        Exception first = null;
        try {
            sessions.close();
        } catch (Exception e) {
            first = e;
        }
        try {
            approvals.close();
        } catch (Exception e) {
            if (first == null) {
                first = e;
            }
        }
        if (first instanceof RuntimeException re) {
            throw re;
        }
        if (first != null) {
            throw new SqliteStoreException("close persist", first);
        }
    }
}
