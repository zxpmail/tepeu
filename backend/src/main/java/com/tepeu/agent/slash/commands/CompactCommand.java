package com.tepeu.agent.slash.commands;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import org.springframework.stereotype.Component;

/**
 * /compact — 提示前端清空本屏对话上下文（不删库中历史）。
 */
@Component
public class CompactCommand implements SlashCommand {

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String description() {
        return "压缩/清空本屏对话上下文（不删除服务器历史）";
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
        return SlashResult.of(
                "已请求压缩本屏上下文。界面将清空当前显示的消息；服务器会话记录仍保留。",
                "compact");
    }
}
