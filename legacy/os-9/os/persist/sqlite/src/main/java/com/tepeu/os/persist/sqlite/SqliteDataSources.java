package com.tepeu.os.persist.sqlite;

import com.tepeu.os.persist.Persist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * SQLite 测试夹具。引擎口是 {@link com.tepeu.os.persist.PersistEngine}。
 * {@link #openFile(Path)} 只给测试。
 */
public final class SqliteDataSources {

    private SqliteDataSources() {
    }

    /** 测试夹具转调 {@link SqliteEngine}。发行路径走 {@link com.tepeu.os.persist.PersistEngines}。 */
    public static void prepare(DataSource ds, String jdbcUrl) {
        new SqliteEngine().prepare(ds, jdbcUrl);
    }

    /** 测试 / 夹具：打开文件并包成 {@link Persist}。调用方关 Persist，不关连接。 */
    public static Persist access(Path file) {
        return Persist.jdbc(openFile(file));
    }

    /** 测试夹具：单连接 + PRAGMA。发行不走这里。 */
    public static SqliteDataSource openFile(Path file) {
        Objects.requireNonNull(file, "file");
        String url = Sqlite.jdbcUrl(file);
        SqliteDataSource ds = new SqliteDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl(url);
        ds.setSuppressClose(true);
        ds.setAutoCommit(true);
        prepare(ds, url);
        return ds;
    }

    public static void script(DataSource ds, String ddl) {
        Objects.requireNonNull(ds, "ds");
        Objects.requireNonNull(ddl, "ddl");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        for (String stmt : ddl.split(";")) {
            String sql = stmt.strip();
            if (Sqlite.executable(sql)) {
                jdbc.execute(sql);
            }
        }
    }

    /** 关后 fail-closed。测试夹具。 */
    public static final class SqliteDataSource extends SingleConnectionDataSource implements AutoCloseable {
        private volatile boolean closed;

        @Override
        public Connection getConnection() throws SQLException {
            if (closed) {
                throw new SQLException("store closed");
            }
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            if (closed) {
                throw new SQLException("store closed");
            }
            return super.getConnection(username, password);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            destroy();
        }
    }
}
