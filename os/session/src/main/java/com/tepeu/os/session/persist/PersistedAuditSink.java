package com.tepeu.os.session.persist;

import com.tepeu.os.session.AuditRecord;
import com.tepeu.os.session.AuditSink;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 人手审计落同一会话库；不进 entries。 */
final class PersistedAuditSink implements AuditSink {

    private final PersistedSessionStore store;

    PersistedAuditSink(PersistedSessionStore store) {
        this.store = store;
    }

    @Override
    public long record(String actor, String action, String detail, Map<String, String> attrs) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
        Map<String, String> a = attrs == null ? Map.of() : attrs;
        return store.tx(status -> {
            JdbcTemplate jdbc = store.jdbc();
            long seq = jdbc.queryForObject("SELECT COALESCE(MAX(seq),0)+1 FROM audit", Long.class);
            jdbc.update("INSERT INTO audit(seq, at_millis, actor, action, detail, attrs) VALUES (?,?,?,?,?,?)",
                    seq,
                    store.clock().instant().toEpochMilli(),
                    actor,
                    action,
                    detail,
                    AttrsJson.write(a));
            return seq;
        });
    }

    @Override
    public List<AuditRecord> readAll() {
        return store.tx(status -> List.copyOf(store.jdbc().query(
                "SELECT seq, at_millis, actor, action, detail, attrs FROM audit ORDER BY seq",
                (rs, i) -> new AuditRecord(
                        rs.getLong(1),
                        Instant.ofEpochMilli(rs.getLong(2)),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        AttrsJson.read(rs.getString(6))))));
    }
}
