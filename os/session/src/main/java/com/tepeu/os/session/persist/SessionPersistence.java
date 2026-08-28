package com.tepeu.os.session.persist;

import com.tepeu.os.persist.Persist;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.SessionStore;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * 用 {@link Persist} 落会话。不碰连接 / 关闭。persist 不知道本类。
 */
public final class SessionPersistence {

    private final PersistedSessionStore sessions;
    private final PersistedAuditSink audit;

    private SessionPersistence(PersistedSessionStore sessions, PersistedAuditSink audit) {
        this.sessions = sessions;
        this.audit = audit;
    }

    public static SessionPersistence open(Persist persist) {
        return open(persist, Clock.systemUTC());
    }

    public static SessionPersistence open(Persist persist, Clock clock) {
        Objects.requireNonNull(persist, "persist");
        persist.script(SessionSchema.DDL);
        SessionDb db = new SessionDb(persist);
        db.tx(status -> {
            db.jdbc.update("INSERT OR IGNORE INTO meta(k, v) VALUES('schema_version', ?)",
                    String.valueOf(SessionSchema.VERSION));
            List<String> rows = db.jdbc.query("SELECT v FROM meta WHERE k=?",
                    (rs, i) -> rs.getString(1), "schema_version");
            if (rows.isEmpty()) {
                throw new IllegalStateException("missing schema_version");
            }
            int version = Integer.parseInt(rows.get(0));
            if (version != SessionSchema.VERSION) {
                throw new IllegalStateException("unsupported schema_version " + version);
            }
            return null;
        });
        PersistedSessionStore sessions = new PersistedSessionStore(db, clock);
        return new SessionPersistence(sessions, new PersistedAuditSink(sessions));
    }

    public SessionStore sessions() {
        return sessions;
    }

    public AuditSink audit() {
        return audit;
    }
}
