package com.tepeu.agent.multi;

/**
 * 任务契约：目标 + 验收标准（不只靠 Prompt）。
 * 关联：MultiAgentOrchestrator、MultiAgentRunRequest。
 */
public record Goal(String objective, String acceptanceCriteria) {

    public Goal {
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("objective is required");
        }
        if (acceptanceCriteria == null || acceptanceCriteria.isBlank()) {
            acceptanceCriteria = "目标完成且结果可检查；失败步骤不得静默忽略。";
        }
    }
}
