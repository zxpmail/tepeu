package com.tepeu.policy.persist;

import com.tepeu.identity.InvokeContext;
import com.tepeu.persist.Persist;
import com.tepeu.persist.PersistRecord;
import com.tepeu.policy.ApprovalRecord;
import com.tepeu.policy.ApprovalStore;
import com.tepeu.syscall.ArgDigest;
import com.tepeu.syscall.Syscall;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 审批走 persist-api。记录 space {@code approval}（键 approvalId），
 * 幂等索引 space {@code approval-pending}（键为会话+名称+指纹的摘要，指向最新审批）。
 * persist 无删除：决策与消费都是覆盖写（decidedAt / consumedAt 置章）。
 */
public final class PersistedApprovalStore implements ApprovalStore {

    private static final String SPACE = "approval";
    private static final String PENDING = "approval-pending";

    private final Persist persist;
    private final Clock clock;

    public PersistedApprovalStore(Persist persist) {
        this(persist, Clock.systemUTC());
    }

    public PersistedApprovalStore(Persist persist, Clock clock) {
        this.persist = Objects.requireNonNull(persist, "persist");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String ask(InvokeContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String pendingKey = pendingKey(ctx, syscall);
        Optional<String> openId = persist.get(PENDING, pendingKey)
                .map(row -> row.fields().get("approvalId"));
        if (openId.isPresent() && !requireRecord(openId.get()).decided()) {
            return openId.get();
        }
        ApprovalRecord asked = new ApprovalRecord(
                "ap-" + (persist.list(SPACE).size() + 1),
                ctx.sessionId().value(),
                syscall.name(),
                ArgDigest.of(syscall.args()),
                clock.instant(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        persist.append(SPACE, new PersistRecord(asked.approvalId(), toFields(asked)));
        persist.put(PENDING, new PersistRecord(pendingKey, Map.of("approvalId", asked.approvalId())));
        return asked.approvalId();
    }

    @Override
    public void decide(String approvalId, boolean allow, String decidedBy) {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(decidedBy, "decidedBy");
        ApprovalRecord record = requireRecord(approvalId);
        if (record.decided()) {
            throw new IllegalStateException("already decided: " + approvalId);
        }
        write(new ApprovalRecord(
                record.approvalId(),
                record.sessionId(),
                record.syscallName(),
                record.argsDigest(),
                record.askedAt(),
                Optional.of(clock.instant()),
                Optional.of(allow),
                Optional.of(decidedBy),
                Optional.empty()));
    }

    @Override
    public Optional<Boolean> consumeDecision(InvokeContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        Optional<String> approvalId = persist.get(PENDING, pendingKey(ctx, syscall))
                .map(row -> row.fields().get("approvalId"));
        if (approvalId.isEmpty()) {
            return Optional.empty();
        }
        ApprovalRecord record = requireRecord(approvalId.get());
        if (!record.decided() || record.consumed()) {
            return Optional.empty();
        }
        write(new ApprovalRecord(
                record.approvalId(),
                record.sessionId(),
                record.syscallName(),
                record.argsDigest(),
                record.askedAt(),
                record.decidedAt(),
                record.allow(),
                record.decidedBy(),
                Optional.of(clock.instant())));
        return record.allow();
    }

    @Override
    public Optional<ApprovalRecord> get(String approvalId) {
        Objects.requireNonNull(approvalId, "approvalId");
        return persist.get(SPACE, approvalId).map(PersistedApprovalStore::toRecord);
    }

    @Override
    public List<ApprovalRecord> records() {
        return persist.list(SPACE).stream().map(PersistedApprovalStore::toRecord).toList();
    }

    private ApprovalRecord requireRecord(String approvalId) {
        return persist.get(SPACE, approvalId).map(PersistedApprovalStore::toRecord)
                .orElseThrow(() -> new IllegalArgumentException("unknown approvalId: " + approvalId));
    }

    private void write(ApprovalRecord record) {
        persist.put(SPACE, new PersistRecord(record.approvalId(), toFields(record)));
    }

    private static String pendingKey(InvokeContext ctx, Syscall syscall) {
        return ArgDigest.of(Map.of(
                "session", ctx.sessionId().value(),
                "name", syscall.name(),
                "args", ArgDigest.of(syscall.args())));
    }

    private static Map<String, String> toFields(ApprovalRecord record) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sessionId", record.sessionId());
        fields.put("syscallName", record.syscallName());
        fields.put("argsDigest", record.argsDigest());
        fields.put("askedAt", record.askedAt().toString());
        fields.put("decidedAt", record.decidedAt().map(Instant::toString).orElse(""));
        fields.put("allow", record.allow().map(Object::toString).orElse(""));
        fields.put("decidedBy", record.decidedBy().orElse(""));
        fields.put("consumedAt", record.consumedAt().map(Instant::toString).orElse(""));
        return fields;
    }

    private static ApprovalRecord toRecord(PersistRecord row) {
        Map<String, String> fields = row.fields();
        return new ApprovalRecord(
                row.key(),
                fields.get("sessionId"),
                fields.get("syscallName"),
                fields.get("argsDigest"),
                Instant.parse(fields.get("askedAt")),
                instant(fields.get("decidedAt")),
                allow(fields.get("allow")),
                text(fields.get("decidedBy")),
                instant(fields.get("consumedAt")));
    }

    private static Optional<Instant> instant(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static Optional<String> text(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Boolean> allow(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if ("true".equals(value) || "false".equals(value)) {
            return Optional.of(Boolean.parseBoolean(value));
        }
        throw new IllegalStateException("approval allow corrupt: " + value);
    }
}
