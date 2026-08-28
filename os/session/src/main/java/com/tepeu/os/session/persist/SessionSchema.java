package com.tepeu.os.session.persist;

/** 会话库 DDL。persist 只负责执行，不拥有这些表。 */
final class SessionSchema {

    static final int VERSION = 1;

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

    private SessionSchema() {
    }
}
