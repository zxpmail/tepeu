package com.tepeu.agent.multi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 三角色系统提示；可通过 tepeu.multi-agent.prompts.* 覆盖默认文案。
 * 关联：MultiAgentOrchestrator、application.yml。
 */
@Component
public class AgentRolePrompts {

    private final String plannerOverride;
    private final String implementerOverride;
    private final String reviewerOverride;

    public AgentRolePrompts(
            @Value("${tepeu.multi-agent.prompts.planner:}") String plannerOverride,
            @Value("${tepeu.multi-agent.prompts.implementer:}") String implementerOverride,
            @Value("${tepeu.multi-agent.prompts.reviewer:}") String reviewerOverride) {
        this.plannerOverride = plannerOverride;
        this.implementerOverride = implementerOverride;
        this.reviewerOverride = reviewerOverride;
    }

    public String planner(Goal goal) {
        String tpl = blank(plannerOverride) ? DEFAULT_PLANNER : plannerOverride;
        return format(tpl, goal.objective(), goal.acceptanceCriteria(), "", "");
    }

    public String implementer(Goal goal, String plan) {
        String tpl = blank(implementerOverride) ? DEFAULT_IMPLEMENTER : implementerOverride;
        return format(tpl, goal.objective(), goal.acceptanceCriteria(), plan == null ? "" : plan, "");
    }

    public String reviewer(Goal goal, String plan, String implementerReport) {
        String tpl = blank(reviewerOverride) ? DEFAULT_REVIEWER : reviewerOverride;
        return format(
                tpl,
                goal.objective(),
                goal.acceptanceCriteria(),
                plan == null ? "" : plan,
                implementerReport == null ? "" : implementerReport);
    }

    /**
     * 占位符：{objective} {acceptance} {plan} {implementerReport}
     */
    static String format(String template, String objective, String acceptance, String plan, String report) {
        return template
                .replace("{objective}", objective == null ? "" : objective)
                .replace("{acceptance}", acceptance == null ? "" : acceptance)
                .replace("{plan}", plan == null ? "" : plan)
                .replace("{implementerReport}", report == null ? "" : report);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static final String DEFAULT_PLANNER = """
            你是 Planner。只做计划，不调用工具，不写完整实现。
            目标：{objective}
            验收标准：{acceptance}
            输出：编号步骤列表（每步一句可执行动作），最后一行写 PLAN_DONE。
            """;

    static final String DEFAULT_IMPLEMENTER = """
            你是 Implementer。按计划在共享工作区执行，可使用工具。
            目标：{objective}
            验收标准：{acceptance}
            计划：
            {plan}
            规则：失败必须说明原因；不要假装成功。完成后简要汇报做了什么。
            """;

    static final String DEFAULT_REVIEWER = """
            你是 Reviewer。对照验收标准审查 Implementer 产出。
            可用只读工具（list_dir / read_file / search_files）核验工作区，禁止修改文件或执行 shell。
            目标：{objective}
            验收标准：{acceptance}
            计划：
            {plan}
            Implementer 汇报：
            {implementerReport}
            输出简短审查意见，最后一行必须是且仅是：
            VERDICT: PASS
            或
            VERDICT: FAIL
            """;
}
