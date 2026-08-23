package com.tepeu.os.session.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

final class Sqlite {

    static final int SCHEMA_VERSION = 1;

    static final String DDL = """
            CREATE TABLE IF NOT EXISTS meta (
              k TEXT PRIMARY KEY,
              v TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sessions (
              id TEXT PRIMARY KEY,
              owner_id TEXT NOT NULL,
              owner_kind TEXT NOT NULL,
              owner_display TEXT,
              workspace_id TEXT NOT NULL,
              tenant_id TEXT,
              parent_id TEXT,
              fork_from TEXT,
              seed_end INTEGER NOT NULL DEFAULT 0,
              surface_explicit INTEGER NOT NULL DEFAULT 0,
              enqueued INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS events (
              session_id TEXT NOT NULL,
              seq INTEGER NOT NULL,
              type TEXT NOT NULL,
              type_version INTEGER NOT NULL DEFAULT 1,
              at_millis INTEGER NOT NULL,
              body TEXT NOT NULL,
              attrs TEXT NOT NULL,
              PRIMARY KEY (session_id, seq)
            );
            CREATE TABLE IF NOT EXISTS surface (
              session_id TEXT NOT NULL,
              ordinal INTEGER NOT NULL,
              seq INTEGER NOT NULL,
              PRIMARY KEY (session_id, ordinal)
            );
            CREATE TABLE IF NOT EXISTS registers (
              session_id TEXT NOT NULL,
              k TEXT NOT NULL,
              v TEXT NOT NULL,
              PRIMARY KEY (session_id, k)
            );
            CREATE TABLE IF NOT EXISTS ledger (
              session_id TEXT NOT NULL,
              seq INTEGER NOT NULL,
              at_millis INTEGER NOT NULL,
              syscall_name TEXT NOT NULL,
              input_tokens INTEGER NOT NULL,
              output_tokens INTEGER NOT NULL,
              cache_read INTEGER NOT NULL,
              cache_write INTEGER NOT NULL,
              cost TEXT,
              attrs TEXT NOT NULL,
              PRIMARY KEY (session_id, seq)
            );
            CREATE TABLE IF NOT EXISTS inbox (
              session_id TEXT NOT NULL,
              message_id TEXT NOT NULL,
              body TEXT NOT NULL,
              source TEXT,
              priority TEXT NOT NULL,
              claim_id TEXT,
              expires_at INTEGER,
              enq INTEGER NOT NULL,
              PRIMARY KEY (session_id, message_id)
            );
            CREATE TABLE IF NOT EXISTS blobs (
              digest TEXT PRIMARY KEY,
              bytes BLOB NOT NULL
            );
            CREATE TABLE IF NOT EXISTS audit (
              seq INTEGER PRIMARY KEY,
              at_millis INTEGER NOT NULL,
              actor TEXT NOT NULL,
              action TEXT NOT NULL,
              detail TEXT NOT NULL,
              attrs TEXT NOT NULL
            );
            """;

    private Sqlite() {
    }

    static String jdbcUrl(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    static Connection open(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            Class.forName("org.sqlite.JDBC");
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection c = DriverManager.getConnection(jdbcUrl(file));
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA synchronous=FULL");
            }
            return c;
        } catch (Exception e) {
            throw new SqliteStoreException("open sqlite " + file, e);
        }
    }

    static void migrate(Connection c) {
        try (Statement s = c.createStatement()) {
            for (String stmt : DDL.split(";")) {
                String sql = stmt.strip();
                if (!sql.isEmpty()) {
                    s.execute(sql);
                }
            }
            s.execute("INSERT OR IGNORE INTO meta(k, v) VALUES('schema_version', '" + SCHEMA_VERSION + "')");
            try (var rs = s.executeQuery("SELECT v FROM meta WHERE k='schema_version'")) {
                if (!rs.next()) {
                    throw new SqliteStoreException("missing schema_version", null);
                }
                int version = Integer.parseInt(rs.getString(1));
                if (version != SCHEMA_VERSION) {
                    throw new SqliteStoreException("unsupported schema_version " + version, null);
                }
            }
        } catch (SQLException e) {
            throw new SqliteStoreException("migrate", e);
        }
    }
}
