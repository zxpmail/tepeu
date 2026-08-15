package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.service.BudgetService;
import com.tepeu.service.SessionService;
import com.tepeu.service.WorkspaceService;
import org.springframework.stereotype.Component;

/**
 * /status — 工作区 + 预算 + 会话概览。
 */
@Component
public class StatusCommand implements SlashCommand {

    private final WorkspaceService workspaceService;
    private final BudgetService budgetService;
    private final SessionService sessionService;

    public StatusCommand(
            WorkspaceService workspaceService,
            BudgetService budgetService,
            SessionService sessionService) {
        this.workspaceService = workspaceService;
        this.budgetService = budgetService;
        this.sessionService = sessionService;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "工作区状态与预算概览";
    }

    @Override
    public String usage() {
        return "/status";
    }

    @Override
    public SlashResult execute(SlashContext ctx) {
        var ws = workspaceService.getWorkspace(ctx.workspaceId())
                .orElseThrow(() -> new IllegalArgumentException("工作区不存在：" + ctx.workspaceId()));
        BudgetService.BudgetStatus budget = budgetService.status(ctx.workspaceId());
        int sessions = sessionService.listSessions(ctx.workspaceId()).size();

        String budgetLine;
        if (budget.budgetUsd() == null) {
            budgetLine = "未设置预算上限";
        } else {
            budgetLine = String.format(
                    "预算 $%.2f · 已用 $%.4f（%.0f%%）%s%s",
                    budget.budgetUsd(),
                    budget.totalCostUsd(),
                    budget.usageRatio() * 100,
                    budget.alert() ? " · 告警" : "",
                    budget.blocked() ? " · 已阻断" : "");
        }

        String sessionLine = ctx.sessionId() == null || ctx.sessionId().isBlank()
                ? "当前无活动会话"
                : "当前会话：" + ctx.sessionId();

        String text = String.format(
                "状态%n• 工作区：%s%n• 会话数：%d%n• %s%n• %s%n• 用量：%d token / %d 回合",
                ws.getName(),
                sessions,
                sessionLine,
                budgetLine,
                budget.totalTokens(),
                budget.turnCount());
        return SlashResult.text(text);
    }
}
