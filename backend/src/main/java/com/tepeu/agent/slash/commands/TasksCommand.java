package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.service.TaskService;
import org.springframework.stereotype.Component;

/**
 * /tasks — 当前工作区任务用量摘要。
 */
@Component
public class TasksCommand implements SlashCommand {

    private final TaskService taskService;

    public TasksCommand(TaskService taskService) {
        this.taskService = taskService;
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
        TaskService.WorkspaceStats s = taskService.getWorkspaceStats(ctx.workspaceId());
        String text = String.format(
                "工作区任务摘要%n• 回合数：%d%n• Token：%d%n• 费用：约 $%.4f",
                s.turnCount(),
                s.totalTokens(),
                s.totalCostUsd());
        return SlashResult.text(text);
    }
}
