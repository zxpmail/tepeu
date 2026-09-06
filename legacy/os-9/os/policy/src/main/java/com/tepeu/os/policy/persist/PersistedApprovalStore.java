package com.tepeu.os.policy.persist;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.ArgDigest;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.persist.Persist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批适配器 — SQL 在这里，Spring JDBC 执行。
 * <p>
 * 不建连接、不 {@code close} Persist。键是 (session, syscall, argsDigest)，不单绑名字。
 * 行数据是 ApprovalStore 真相，不写 slf4j、不打 SQL 行。人手盖章走 Slash + AuditSink。
 */
final class PersistedApprovalStore implements ApprovalStore {

    private final Persist persist;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    PersistedApprovalStore(Persist persist, Clock clock) {
        this.persist = persist;
        this.jdbc = persist.jdbc();
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    private <T> T inTx(TransactionCallback<T> work) {
        return persist.tx(work);
    }

    /**
     * 登记 asked。同一会话、同一 syscall、同一 digest 若已有未决单，幂等返回该 id。
     */
    @Override
    public String ask(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        return inTx(status -> {
            List<String> open = jdbc.query(
                    "SELECT approval_id FROM approvals WHERE session_id=? AND syscall_name=? "
                            + "AND args_digest=? AND decided_at IS NULL ORDER BY asked_at DESC LIMIT 1",
                    (rs, i) -> rs.getString(1),
                    sessionId, syscallName, digest);
            if (!open.isEmpty()) {
                return open.get(0);
            }
            String approvalId = UUID.randomUUID().toString();
            jdbc.update(
                    "INSERT INTO approvals(approval_id, session_id, syscall_name, args_digest, asked_at) "
                            + "VALUES (?,?,?,?,?)",
                    approvalId, sessionId, syscallName, digest, clock.instant().toEpochMilli());
            return approvalId;
        });
    }

    /**
     * 落 decided。未知 id 或已决则抛，不覆盖。本方法不写 AuditSink（人手路径在 compose）。
     */
    @Override
    public void decide(String approvalId, boolean allow, String decidedBy) {
        Objects.requireNonNull(approvalId, "approvalId");
        inTx(status -> {
            List<Long> rows = jdbc.query("SELECT decided_at FROM approvals WHERE approval_id=?",
                    (rs, i) -> {
                        long v = rs.getLong(1);
                        return rs.wasNull() ? null : v;
                    }, approvalId);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("unknown approvalId: " + approvalId);
            }
            if (rows.get(0) != null) {
                throw new IllegalStateException("approval already decided: " + approvalId);
            }
            jdbc.update("UPDATE approvals SET decided_at=?, allow_flag=?, decided_by=? WHERE approval_id=?",
                    clock.instant().toEpochMilli(), allow ? 1 : 0, decidedBy, approvalId);
            return null;
        });
    }

    /**
     * 取走最新一张同键单的决策。未决、已消费、无行 → empty（总线再 ASK / DENY）。
     * 取走即 {@code consumed=1}，不能再用。
     */
    @Override
    public Optional<Boolean> consumeDecision(TurnContext ctx, Syscall syscall) {
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        return inTx(status -> {
            List<ConsumeRow> rows = jdbc.query(
                    "SELECT approval_id, decided_at, allow_flag, consumed FROM approvals "
                            + "WHERE session_id=? AND syscall_name=? AND args_digest=? "
                            + "ORDER BY asked_at DESC LIMIT 1",
                    (rs, i) -> new ConsumeRow(
                            rs.getString(1),
                            nullableLong(rs, 2),
                            nullableInt(rs, 3),
                            rs.getInt(4) != 0),
                    sessionId, syscallName, digest);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            ConsumeRow row = rows.get(0);
            boolean decided = row.decidedAt != null;
            Boolean allow = decided && row.allowFlag != null ? row.allowFlag != 0 : null;
            if (decided && !row.consumed && allow != null) {
                jdbc.update("UPDATE approvals SET consumed=1 WHERE approval_id=?", row.approvalId);
                return Optional.of(allow);
            }
            return Optional.empty();
        });
    }

    /** 按 id 读；不存在 empty。Slash {@code /approve} 用它校验本会话。 */
    @Override
    public Optional<ApprovalRecord> get(String approvalId) {
        Objects.requireNonNull(approvalId, "approvalId");
        return inTx(status -> {
            List<ApprovalRecord> rows = jdbc.query(
                    "SELECT approval_id, session_id, syscall_name, args_digest, asked_at, decided_at, "
                            + "allow_flag, decided_by FROM approvals WHERE approval_id=?",
                    (rs, i) -> mapRecord(rs),
                    approvalId);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        });
    }

    @Override
    public List<ApprovalRecord> records() {
        return inTx(status -> List.copyOf(jdbc.query(
                "SELECT approval_id, session_id, syscall_name, args_digest, asked_at, decided_at, "
                        + "allow_flag, decided_by FROM approvals ORDER BY asked_at, approval_id",
                (rs, i) -> mapRecord(rs))));
    }

    private static ApprovalRecord mapRecord(ResultSet rs) throws SQLException {
        Long decidedAt = nullableLong(rs, 6);
        Integer allow = nullableInt(rs, 7);
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

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private record ConsumeRow(String approvalId, Long decidedAt, Integer allowFlag, boolean consumed) {
    }
}
