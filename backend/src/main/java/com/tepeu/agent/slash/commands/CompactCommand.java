package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.service.SessionService;
import org.springframework.stereotype.Component;

/**
 * /compact — 清空当前会话在服务器上的消息历史，并通知前端清屏。
 * 下一轮对话不再携带旧上下文（与仅清屏不同）。
 */
@Component
public class CompactCommand implements SlashCommand {

    private final SessionService sessionService;

    public CompactCommand(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String description() {
        return "清空当前会话历史（服务器+本屏），下次对话不再带旧上下文";
    }

    @Override
    public String usage() {
        return "/compact";
    }

    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    @Override
    public SlashResult execute(SlashContext ctx) {
        String sid = ctx.sessionId();
        if (sid == null || sid.isBlank()) {
            return SlashResult.of(
                    "当前没有活动会话。已仅清空本屏显示；发送新消息后会开启新会话。",
                    "compact");
        }
        sessionService.clearMessages(sid);
        return SlashResult.of(
                "已清空本会话的服务器历史与本屏显示。下一轮对话将从空白上下文开始。",
                "compact");
    }
}
