package com.tepeu.agent.hook;

import org.springframework.stereotype.Component;

/**
 * 将 REST 文件 / 终端 WS 纳入与 Agent 工具相同的 Hook + ApprovalStore 门禁。
 * 关联：FileController、TerminalWebSocketHandler、DangerousToolHook。
 */
@Component
public class HostChannelGuard {

    public record GateResult(
            boolean allowed,
            String code,
            String message,
            String approvalId) {

        public static GateResult allow() {
            return new GateResult(true, null, null, null);
        }

        public static GateResult deny(String message) {
            return new GateResult(false, "DENIED", message, null);
        }

        public static GateResult needApproval(String approvalId, String message) {
            return new GateResult(false, "APPROVAL_REQUIRED", message, approvalId);
        }

        public static GateResult timeout(String approvalId) {
            return new GateResult(false, "APPROVAL_TIMEOUT",
                    "审批超时，请重试", approvalId);
        }
    }

    private final ToolHook toolHook;
    private final ApprovalStore approvalStore;

    public HostChannelGuard(ToolHook toolHook, ApprovalStore approvalStore) {
        this.toolHook = toolHook;
        this.approvalStore = approvalStore;
    }

    /**
     * @param channelSessionId 通道会话（rest-&lt;wsId&gt; / terminal-&lt;wsId&gt;）
     * @param toolName         合成工具名（rest_delete_file / terminal_shell 等）
     * @param argsJson         参数 JSON
     * @param blockWait        true 时阻塞等待批准（终端可选）；REST 建议 false
     */
    public GateResult check(String channelSessionId, String toolName, String argsJson, boolean blockWait) {
        String sid = channelSessionId == null || channelSessionId.isBlank()
                ? "host-anonymous" : channelSessionId;
        ToolHook.Verdict verdict = toolHook.evaluate(toolName, argsJson);
        if (verdict == ToolHook.Verdict.ALLOW) {
            return GateResult.allow();
        }
        if (verdict == ToolHook.Verdict.DENY) {
            return GateResult.deny("ERROR: DENIED tool='" + toolName + "'。策略禁止执行。");
        }
        if (approvalStore.hasSessionGrant(sid, toolName, argsJson)) {
            return GateResult.allow();
        }
        ApprovalStore.Approval pending = approvalStore.createPending(sid, toolName, argsJson);
        if (!blockWait || approvalStore.approvalWait().isZero()) {
            return GateResult.needApproval(pending.id(),
                    "需要批准后才能执行：" + toolName);
        }
        var decided = approvalStore.awaitDecision(pending.id());
        if (decided.isPresent() && decided.get() == ApprovalStore.Status.APPROVED) {
            return GateResult.allow();
        }
        if (decided.isPresent() && decided.get() == ApprovalStore.Status.DENIED) {
            return GateResult.deny("ERROR: DENIED tool='" + toolName + "'。用户已拒绝。");
        }
        return GateResult.timeout(pending.id());
    }

    /** 在已发出 APPROVAL_REQUIRED 后阻塞等待用户裁决。 */
    public GateResult awaitPending(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            return GateResult.deny("缺少审批编号");
        }
        if (approvalStore.approvalWait().isZero()) {
            return GateResult.needApproval(approvalId, "等待用户批准");
        }
        var decided = approvalStore.awaitDecision(approvalId);
        if (decided.isPresent() && decided.get() == ApprovalStore.Status.APPROVED) {
            return GateResult.allow();
        }
        if (decided.isPresent() && decided.get() == ApprovalStore.Status.DENIED) {
            return GateResult.deny("ERROR: DENIED。用户已拒绝。");
        }
        return GateResult.timeout(approvalId);
    }
}
