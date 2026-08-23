package com.tepeu.os.policy.memory;

import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.ArgDigest;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.identity.TurnContext;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 单机内存审批通道 — conformance/测试用。生产默认 SQLite：审批是合规证据，
 * 禁内存默认（ADR-016 ApprovalStore 行）。ask 幂等（同 (session, syscall, argsDigest) 未决同 id）；
 * decide 一次；consumeDecision 取走即消费（许可严格单次，第九轮 C1）。
 */
public final class InMemoryApprovalStore implements ApprovalStore {

    private final Clock clock;
    private final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        ApprovalRecord record;
        boolean consumed;

        Entry(ApprovalRecord record) {
            this.record = record;
        }
    }

    public InMemoryApprovalStore() {
        this(Clock.systemUTC());
    }

    public InMemoryApprovalStore(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized String ask(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        for (int i = entries.size() - 1; i >= 0; i--) {
            ApprovalRecord r = entries.get(i).record;
            if (sessionId.equals(r.sessionId()) && syscallName.equals(r.syscallName())
                    && digest.equals(r.argsDigest()) && !r.decided()) {
                return r.approvalId();
            }
        }
        String approvalId = UUID.randomUUID().toString();
        entries.add(new Entry(new ApprovalRecord(approvalId, sessionId, syscallName, digest,
                clock.instant(), null, null, null)));
        return approvalId;
    }

    @Override
    public synchronized void decide(String approvalId, boolean allow, String decidedBy) {
        Entry e = find(approvalId);
        if (e.record.decided()) {
            throw new IllegalStateException("approval already decided: " + approvalId);
        }
        Instant at = clock.instant();
        e.record = new ApprovalRecord(e.record.approvalId(), e.record.sessionId(),
                e.record.syscallName(), e.record.argsDigest(), e.record.askedAt(), Optional.of(at),
                Optional.of(allow), Optional.ofNullable(decidedBy));
    }

    @Override
    public synchronized Optional<Boolean> consumeDecision(TurnContext ctx, Syscall syscall) {
        String sessionId = ctx.sessionId().value();
        String syscallName = syscall.name();
        String digest = ArgDigest.of(syscall.args());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            ApprovalRecord r = e.record;
            if (sessionId.equals(r.sessionId()) && syscallName.equals(r.syscallName())
                    && digest.equals(r.argsDigest())) {
                if (r.decided() && !e.consumed) {
                    e.consumed = true;
                    return r.allow();
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized Optional<ApprovalRecord> get(String approvalId) {
        for (Entry e : entries) {
            if (e.record.approvalId().equals(Objects.requireNonNull(approvalId, "approvalId"))) {
                return Optional.of(e.record);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<ApprovalRecord> records() {
        return entries.stream().map(e -> e.record).toList();
    }

    private Entry find(String approvalId) {
        for (Entry e : entries) {
            if (e.record.approvalId().equals(Objects.requireNonNull(approvalId, "approvalId"))) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown approvalId: " + approvalId);
    }
}
