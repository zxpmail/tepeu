package com.tepeu.dispatch;

import com.tepeu.identity.InvokeContext;
import com.tepeu.policy.ApprovalRecord;
import com.tepeu.policy.ApprovalStore;
import com.tepeu.syscall.ArgDigest;
import com.tepeu.syscall.Syscall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * dispatch 测试专用假审批。只够 dispatch 契约：ask 未决幂等、decide 一次、consume 单次。
 * {@link #get} / {@link #records} dispatch 不用，恒空。
 */
final class FakeApprovalStore implements ApprovalStore {

    private final Map<String, String> current = new LinkedHashMap<>();
    private final Map<String, Boolean> verdicts = new LinkedHashMap<>();
    private final Map<String, Boolean> consumed = new LinkedHashMap<>();

    private static String key(InvokeContext ctx, Syscall syscall) {
        return ctx.sessionId().value() + "|" + syscall.name() + "|" + ArgDigest.of(syscall.args());
    }

    @Override
    public String ask(InvokeContext ctx, Syscall syscall) {
        String key = key(ctx, syscall);
        String id = current.get(key);
        if (id != null && !verdicts.containsKey(id)) {
            return id;
        }
        String fresh = "ap-" + (current.size() + 1);
        current.put(key, fresh);
        return fresh;
    }

    @Override
    public void decide(String approvalId, boolean allow, String decidedBy) {
        verdicts.put(approvalId, allow);
    }

    @Override
    public Optional<Boolean> consumeDecision(InvokeContext ctx, Syscall syscall) {
        String id = current.get(key(ctx, syscall));
        if (id == null || !verdicts.containsKey(id) || consumed.containsKey(id)) {
            return Optional.empty();
        }
        consumed.put(id, true);
        return Optional.of(verdicts.get(id));
    }

    @Override
    public Optional<ApprovalRecord> get(String approvalId) {
        return Optional.empty();
    }

    @Override
    public List<ApprovalRecord> records() {
        return List.of();
    }
}
