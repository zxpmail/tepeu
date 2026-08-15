package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashCommandRegistry;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * /help — 列出内置 Slash 命令。
 * 通过 setter 注入 Registry，打破与 Registry 构造期的循环依赖。
 */
@Component
public class HelpCommand implements SlashCommand {

    private SlashCommandRegistry registry;

    @Autowired
    public void setRegistry(@Lazy SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "列出内置 Slash 命令";
    }

    @Override
    public String usage() {
        return "/help";
    }

    @Override
    public boolean requiresWorkspace() {
        return false;
    }

    @Override
    public SlashResult execute(SlashContext ctx) {
        if (registry == null) {
            return SlashResult.text("命令目录尚未就绪。");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("内置命令（不消耗模型额度）：\n");
        for (SlashCommand c : registry.list()) {
            sb.append("• ").append(c.usage())
                    .append(" — ").append(c.description())
                    .append('\n');
        }
        sb.append("\n另有界面命令：/clear /new /files（仅前端）");
        return SlashResult.text(sb.toString().trim());
    }
}
