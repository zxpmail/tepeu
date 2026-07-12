package com.tepeu.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final String SCHEMA_SQL = """
        CREATE TABLE IF NOT EXISTS workspace (
            id            TEXT PRIMARY KEY,
            name          TEXT NOT NULL,
            description   TEXT,
            type          TEXT CHECK(type IN ('personal', 'enterprise')),
            owner_id      TEXT NOT NULL,
            root_path     TEXT,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS session (
            id            TEXT PRIMARY KEY,
            workspace_id  TEXT NOT NULL,
            title         TEXT,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS memory (
            id            TEXT PRIMARY KEY,
            workspace_id  TEXT NOT NULL,
            source        TEXT NOT NULL,
            content       TEXT NOT NULL,
            tags          TEXT DEFAULT '[]',
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS task (
            id            TEXT PRIMARY KEY,
            workspace_id  TEXT NOT NULL,
            session_id    TEXT,
            status        TEXT CHECK(status IN ('pending', 'running', 'completed', 'failed', 'cancelled')),
            outcome       TEXT CHECK(outcome IN ('succeeded', 'partial', 'abandoned')),
            model_used    TEXT,
            tokens_used   INTEGER,
            cost_usd      REAL,
            started_at    DATETIME,
            completed_at  DATETIME,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS file_version (
            id            TEXT PRIMARY KEY,
            workspace_id  TEXT NOT NULL,
            file_path     TEXT NOT NULL,
            version_no    INTEGER NOT NULL,
            content_ref   TEXT NOT NULL,
            created_by_session TEXT,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS llm_provider (
            id            TEXT PRIMARY KEY,
            provider_id   TEXT NOT NULL,
            api_key       TEXT NOT NULL,
            base_url      TEXT,
            default_model TEXT,
            enabled       INTEGER DEFAULT 1,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS message (
            id            TEXT PRIMARY KEY,
            session_id    TEXT NOT NULL,
            role          TEXT NOT NULL CHECK(role IN ('user','assistant','system')),
            content       TEXT NOT NULL,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS skill (
            id            TEXT PRIMARY KEY,
            workspace_id  TEXT NOT NULL,
            slug          TEXT NOT NULL,
            name          TEXT NOT NULL,
            description   TEXT,
            content       TEXT NOT NULL,
            enabled       INTEGER DEFAULT 0,
            builtin       INTEGER DEFAULT 0,
            created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(workspace_id, slug),
            FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_memory_workspace ON memory(workspace_id);
        CREATE INDEX IF NOT EXISTS idx_memory_search ON memory(content);
        CREATE INDEX IF NOT EXISTS idx_session_workspace ON session(workspace_id);
        CREATE INDEX IF NOT EXISTS idx_task_workspace ON task(workspace_id);
        CREATE INDEX IF NOT EXISTS idx_file_version_path ON file_version(workspace_id, file_path);
        CREATE INDEX IF NOT EXISTS idx_message_session ON message(session_id);
        CREATE INDEX IF NOT EXISTS idx_skill_workspace ON skill(workspace_id);
        """;

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        // Enable WAL mode (database-file level; persists across connections).
        // foreign_keys=ON is a per-connection pragma, so it is applied to every pooled
        // connection via spring.datasource.hikari.connection-init-sql (see application.yml).
        jt.execute("PRAGMA journal_mode=WAL");
        // Create tables
        for (String stmt : SCHEMA_SQL.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                jt.execute(trimmed);
            }
        }
        // Migrations: add columns that may not exist in databases created before the column was added.
        try {
            jt.execute("ALTER TABLE workspace ADD COLUMN root_path TEXT");
        } catch (Exception ignored) {
            // column already exists — safe to ignore
        }
        try {
            jt.execute("ALTER TABLE session ADD COLUMN parent_session_id TEXT");
        } catch (Exception ignored) {
            // column already exists — safe to ignore
        }
        try {
            jt.execute("ALTER TABLE session ADD COLUMN fork_from_message_id TEXT");
        } catch (Exception ignored) {
            // column already exists — safe to ignore
        }
        return jt;
    }
}
