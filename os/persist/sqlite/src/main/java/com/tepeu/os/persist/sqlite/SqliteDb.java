package com.tepeu.os.persist.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * 一份 SQLite 文件的连接 + 事务。会话库与审批库各一实例，JDBC 栈只此一份。
 */
final class SqliteDb implements AutoCloseable {

    interface Sql<T> {
        T run(Connection c) throws SQLException;
    }

    private final Path file;
    private final Connection conn;
    private final Object lock = new Object();
    private volatile boolean closed;

    private SqliteDb(Path file, Connection conn) {
        this.file = file;
        this.conn = conn;
    }

    static SqliteDb kernel(Path file) {
        Connection conn = Sqlite.open(file);
        Sqlite.migrateKernel(conn);
        return new SqliteDb(file, conn);
    }

    static SqliteDb approvals(Path file) {
        Connection conn = Sqlite.open(file);
        Sqlite.migrateApprovals(conn);
        return new SqliteDb(file, conn);
    }

    Path file() {
        return file;
    }

    <T> T tx(Sql<T> sql) {
        Objects.requireNonNull(sql, "sql");
        synchronized (lock) {
            if (closed) {
                throw new SqliteStoreException("store closed", null);
            }
            try {
                conn.setAutoCommit(false);
                T result = sql.run(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly();
                throw new SqliteStoreException(e);
            } catch (RuntimeException e) {
                rollbackQuietly();
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // keep closed-fail visible on next op
                }
            }
        }
    }

    private void rollbackQuietly() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // original exception already in flight
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                conn.close();
            } catch (SQLException e) {
                throw new SqliteStoreException("close", e);
            }
        }
    }
}
