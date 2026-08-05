package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.service.TaskService;
import com.tepeu.service.WorkspaceService;
import org.springframework.stereotype.Component;

/**
 * /tasks — 当前工作区任务用量摘要。
 */
@Component
public class TasksCommand implements SlashCommand {

    private final TaskService taskService;
    private final WorkspaceService workspaceService;

    public TasksCommand(TaskService taskService, WorkspaceService workspaceService) {
        this.taskService = taskService;
        this.workspaceService = workspaceService;
    }

    @Override
    public String name() {
        return "tasks";
    }

    @Override
    public String description() {
        return "查看工作区任务用量（token / 费用 / 回合）";
    }

    @Override
    public String usage() {
        return "/tasks";
    }

    @Override
    public SlashResult execute(SlashContext ctx) {
        // 与 /status 一致：校验工作区存在，避免伪 id 返回全零摘要
        workspaceService.getWorkspace(ctx.workspaceId())
                .orElseThrow(() -> new IllegalArgumentException("工作区不存在：" + ctx.workspaceId()));
        TaskService.WorkspaceStats s = taskService.getWorkspaceStats(ctx.workspaceId());
        String text = String.format(
                "工作区任务摘要%n• 回合数：%d%n• Token：%d%n• 费用：约 $%.4f",
                s.turnCount(),
                s.totalTokens(),
                s.totalCostUsd());
        return SlashResult.text(text);
    }
}
