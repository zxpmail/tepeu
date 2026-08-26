package com.tepeu.os.persist.sqlite;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.ArgDigest;
import com.tepeu.os.syscall.Syscall;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite ApprovalStore — 发行默认。禁内存默认（ADR-016）。
 * JDBC 走 {@link SqliteDb}，不自开连接。
 */
public final class SqliteApprovalStore implements ApprovalStore, AutoCloseable {

    private final Clock clock;
    private final SqliteDb db;

    public SqliteApprovalStore(Path file) {
        this(file, Clock.systemUTC());
    }

    public SqliteApprovalStore(Path file, Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.db = SqliteDb.approvals(Objects.requireNonNull(file, "file"));
    }

    private <T> T tx(SqliteDb.Sql<T> sql) {
        return db.tx(sql);
    }

    @Override
    public String ask(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        return tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT approval_id FROM approvals WHERE session_id=? AND syscall_name=? "
                            + "AND args_digest=? AND decided_at IS NULL ORDER BY asked_at DESC LIMIT 1")) {
                ps.setString(1, sessionId);
                ps.setString(2, syscallName);
                ps.setString(3, digest);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
            String approvalId = UUID.randomUUID().toString();
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO approvals(approval_id, session_id, syscall_name, args_digest, asked_at) "
                            + "VALUES (?,?,?,?,?)")) {
                ins.setString(1, approvalId);
                ins.setString(2, sessionId);
                ins.setString(3, syscallName);
                ins.setString(4, digest);
                ins.setLong(5, clock.instant().toEpochMilli());
                ins.executeUpdate();
            }
            return approvalId;
        });
    }

    @Override
    public void decide(String approvalId, boolean allow, String decidedBy) {
        Objects.requireNonNull(approvalId, "approvalId");
        tx(c -> {
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT decided_at FROM approvals WHERE approval_id=?")) {
                q.setString(1, approvalId);
                try (ResultSet rs = q.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("unknown approvalId: " + approvalId);
                    }
                    if (rs.getObject(1) != null) {
                        throw new IllegalStateException("approval already decided: " + approvalId);
                    }
                }
            }
            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE approvals SET decided_at=?, allow_flag=?, decided_by=? WHERE approval_id=?")) {
                u.setLong(1, clock.instant().toEpochMilli());
                u.setInt(2, allow ? 1 : 0);
                u.setString(3, decidedBy);
                u.setString(4, approvalId);
                u.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<Boolean> consumeDecision(TurnContext ctx, Syscall syscall) {
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        return tx(c -> {
            String approvalId = null;
            boolean decided = false;
            boolean consumed = false;
            Boolean allow = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT approval_id, decided_at, allow_flag, consumed FROM approvals "
                            + "WHERE session_id=? AND syscall_name=? AND args_digest=? "
                            + "ORDER BY asked_at DESC LIMIT 1")) {
                ps.setString(1, sessionId);
                ps.setString(2, syscallName);
                ps.setString(3, digest);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    approvalId = rs.getString(1);
                    decided = rs.getObject(2) != null;
                    int flag = rs.getInt(3);
                    boolean flagNull = rs.wasNull();
                    consumed = rs.getInt(4) != 0;
                    if (decided && !flagNull) {
                        allow = flag != 0;
                    }
                }
            }
            if (decided && !consumed && allow != null) {
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE approvals SET consumed=1 WHERE approval_id=?")) {
                    u.setString(1, approvalId);
                    u.executeUpdate();
                }
                return Optional.of(allow);
            }
            return Optional.empty();
        });
    }

    @Override
    public Optional<ApprovalRecord> get(String approvalId) {
        Objects.requireNonNull(approvalId, "approvalId");
        return tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT approval_id, session_id, syscall_name, args_digest, asked_at, decided_at, "
                            + "allow_flag, decided_by FROM approvals WHERE approval_id=?")) {
                ps.setString(1, approvalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapRecord(rs));
                }
            }
        });
    }

    @Override
    public List<ApprovalRecord> records() {
        return tx(c -> {
            List<ApprovalRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT approval_id, session_id, syscall_name, args_digest, asked_at, decided_at, "
                            + "allow_flag, decided_by FROM approvals ORDER BY asked_at, approval_id");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRecord(rs));
                }
            }
            return List.copyOf(out);
        });
    }

    private static ApprovalRecord mapRecord(ResultSet rs) throws SQLException {
        Long decidedAt = rs.getObject(6) == null ? null : rs.getLong(6);
        Integer allow = rs.getObject(7) == null ? null : rs.getInt(7);
        String digest = rs.getString(4);
        if (digest == null || digest.isBlank()) {
            digest = ArgDigest.of(Map.of());
        }
        return new ApprovalRecord(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                digest,
                Instant.ofEpochMilli(rs.getLong(5)),
                decidedAt == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(decidedAt)),
                allow == null ? Optional.empty() : Optional.of(allow != 0),
                Optional.ofNullable(rs.getString(8)));
    }

    @Override
    public void close() {
        db.close();
    }
}
