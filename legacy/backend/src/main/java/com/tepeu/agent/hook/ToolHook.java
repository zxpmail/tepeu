package com.tepeu.agent.hook;

/**
 * PreTool 策略：在真实工具执行前判定放行 / 拒绝 / 需用户审批。
 * 关联：DangerousToolHook、HookingToolCallback、ApprovalStore。
 */
public interface ToolHook {

    enum Verdict {
        ALLOW,
        DENY,
        NEED_APPROVAL
    }

    /**
     * @param toolName 工具名（如 write_file）
     * @param argsJson 模型传入的 JSON 参数串
     */
    Verdict evaluate(String toolName, String argsJson);
}
