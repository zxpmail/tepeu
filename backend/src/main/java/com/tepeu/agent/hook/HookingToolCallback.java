package com.tepeu.agent.hook;

import com.tepeu.agent.tool.CommandOutputStore;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.ToolKinds;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PreTool Hook 装饰器：高危工具无会话授权时拦截并申请审批，不执行委托。
 * 装配在 {@code ToolEventEmittingCallback} 内侧（外层仍发 tool_call/tool_result）。
 */
public final class HookingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolHook hook;
    private final ApprovalStore approvalStore;
    private final String sessionId;
    private final ToolEventEmitter emitter;

    public HookingToolCallback(
            ToolCallback delegate,
            ToolHook hook,
            ApprovalStore approvalStore,
            String sessionId,
            ToolEventEmitter emitter) {
        this.delegate = delegate;
        this.hook = hook;
        this.approvalStore = approvalStore;
        this.sessionId = sessionId;
        this.emitter = emitter != null ? emitter : ToolEventEmitter.NOOP;
    }

    /** 将每个回调包一层 Hook（共享 session / store / emitter）。 */
    public static ToolCallback[] wrapAll(
            ToolCallback[] callbacks,
            ToolHook hook,
            ApprovalStore approvalStore,
            String sessionId,
            ToolEventEmitter emitter) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new HookingToolCallback(callbacks[i], hook, approvalStore, sessionId, emitter);
        }
        return wrapped;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        CommandOutputStore.enter(sessionId);
        try {
            String blocked = maybeBlock(toolInput);
            if (blocked != null) {
                return blocked;
            }
            return delegate.call(toolInput);
        } finally {
            CommandOutputStore.exit();
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        CommandOutputStore.enter(sessionId);
        try {
            String blocked = maybeBlock(toolInput);
            if (blocked != null) {
                return blocked;
            }
            return delegate.call(toolInput, toolContext);
        } finally {
            CommandOutputStore.exit();
        }
    }

    /**
     * @return 拦截时返回给模型的错误串；放行返回 null
     */
    private String maybeBlock(String toolInput) {
        String name = delegate.getToolDefinition().name();
        ToolHook.Verdict verdict = hook.evaluate(name, toolInput);

        if (verdict == ToolHook.Verdict.ALLOW) {
            return null;
        }
        if (verdict == ToolHook.Verdict.DENY) {
            String msg = "ERROR: DENIED tool='" + name + "'。策略禁止执行该工具。";
            emitDenied(name, toolInput, msg);
            return msg;
        }

        // 自主调度：仅 shell 免批；MCP / delete_file / 未知类别仍须批（无人值守则本工具失败）
        if (approvalStore.isAutonomous(sessionId) && "shell".equals(ToolKinds.of(name))) {
            return null;
        }

        // NEED_APPROVAL：已有「工具+参数」会话授权则放行
        if (approvalStore.hasSessionGrant(sessionId, name, toolInput)) {
            return null;
        }

        // 复用同会话同工具同参数的 PENDING，避免重试刷出第二条审批
        String sid = sessionId == null ? "" : sessionId;
        boolean alreadyPending = approvalStore.findPending(sid, name, toolInput).isPresent();
        ApprovalStore.Approval pending = approvalStore.createPending(sessionId, name, toolInput);
        if (!alreadyPending) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "tool_approval_required");
            event.put("approvalId", pending.id());
            event.put("tool", name);
            event.put("toolKind", ToolKinds.of(name));
            event.put("sessionId", sid);
            event.put("params", toolInput == null ? "" : toolInput);
            emitter.emit(event);
        }

        // 配置了等待时长：阻塞至用户裁决（多 Agent 面板可批准后继续执行）
        if (!approvalStore.approvalWait().isZero()) {
            var decided = approvalStore.awaitDecision(pending.id());
            if (decided.isPresent() && decided.get() == ApprovalStore.Status.APPROVED) {
                return null;
            }
            if (decided.isPresent() && decided.get() == ApprovalStore.Status.DENIED) {
                String msg = "ERROR: DENIED tool='" + name + "'。用户已拒绝。";
                emitDenied(name, toolInput, msg);
                return msg;
            }
            String msg = "ERROR: APPROVAL_TIMEOUT id=" + pending.id()
                    + " tool='" + name + "'。用户未在时限内批准。";
            emitDenied(name, toolInput, msg);
            return msg;
        }

        return "ERROR: APPROVAL_REQUIRED id=" + pending.id()
                + " tool='" + name
                + "'。需用户在界面批准后才能执行；批准前勿声称已成功，勿重复调用。";
    }

    private void emitDenied(String name, String toolInput, String msg) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "tool_denied");
        event.put("tool", name);
        event.put("toolKind", ToolKinds.of(name));
        event.put("content", msg);
        event.put("params", toolInput == null ? "" : toolInput);
        emitter.emit(event);
    }
}
