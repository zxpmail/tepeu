package com.tepeu.os.persist.sqlite;

import com.tepeu.os.session.AuditRecord;
import com.tepeu.os.session.AuditSink;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 人手审计落同一 kernel.sqlite；不进会话 entries。 */
final class SqliteAuditSink implements AuditSink {

    private final SqliteSessionStore store;

    SqliteAuditSink(SqliteSessionStore store) {
        this.store = store;
    }

    @Override
    public long record(String actor, String action, String detail, Map<String, String> attrs) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
        Map<String, String> a = attrs == null ? Map.of() : attrs;
        return store.tx(c -> {
            long seq;
            try (PreparedStatement q = c.prepareStatement("SELECT COALESCE(MAX(seq),0)+1 FROM audit")) {
                try (ResultSet rs = q.executeQuery()) {
                    rs.next();
                    seq = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO audit(seq, at_millis, actor, action, detail, attrs) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, seq);
                ps.setLong(2, store.clock().instant().toEpochMilli());
                ps.setString(3, actor);
                ps.setString(4, action);
                ps.setString(5, detail);
                ps.setString(6, AttrsJson.write(a));
                ps.executeUpdate();
            }
            return seq;
        });
    }

    @Override
    public List<AuditRecord> readAll() {
        return store.tx(c -> {
            List<AuditRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seq, at_millis, actor, action, detail, attrs FROM audit ORDER BY seq");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AuditRecord(
                            rs.getLong(1),
                            Instant.ofEpochMilli(rs.getLong(2)),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            AttrsJson.read(rs.getString(6))));
                }
            }
            return List.copyOf(out);
        });
    }
}
