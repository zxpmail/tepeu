package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.model.AgentSchedule;
import com.tepeu.service.ScheduleService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * /schedule — 列出（默认）当前工作区自主任务。
 */
@Component
public class ScheduleCommand implements SlashCommand {

    private final ScheduleService scheduleService;

    public ScheduleCommand(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Override
    public String name() {
        return "schedule";
    }

    @Override
    public String description() {
        return "查看自主任务列表";
    }

    @Override
    public String usage() {
        return "/schedule [list]";
    }

    @Override
    public SlashResult execute(SlashContext ctx) {
        String sub = ctx.arg(0);
        if (sub != null && !sub.isBlank()) {
            String s = sub.toLowerCase(Locale.ROOT);
            if (!"list".equals(s) && !"ls".equals(s)) {
                return SlashResult.text("用法：/schedule [list]\n未知子命令：" + sub);
            }
        }
        List<AgentSchedule> items = scheduleService.list(ctx.workspaceId());
        if (items.isEmpty()) {
            return SlashResult.text("当前工作区暂无自主任务。可在侧栏「自主」面板新建。");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("自主任务（").append(items.size()).append("）：\n");
        for (AgentSchedule a : items) {
            sb.append("• ").append(a.getName())
                    .append(" — 每 ").append(a.getIntervalMinutes()).append(" 分钟")
                    .append(a.isEnabled() ? " · 启用" : " · 停用")
                    .append(" · ").append(a.getLastStatus() == null ? "—" : a.getLastStatus());
            if (a.getLastError() != null && !a.getLastError().isBlank()) {
                sb.append("\n  错误：").append(a.getLastError());
            }
            sb.append('\n');
        }
        return SlashResult.text(sb.toString().trim());
    }
}
