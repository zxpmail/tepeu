package com.tepeu.agent.slash;

/**
 * 内置 Slash 命令（不经 LLM）。
 * 关联：SlashCommandRegistry、SlashController。
 */
public interface SlashCommand {

    /** 命令名（不含前导 /） */
    String name();

    /** 一句话说明 */
    String description();

    /** 用法提示，如 {@code /schedule [list]} */
    String usage();

    /** 是否需要有效 workspaceId */
    default boolean requiresWorkspace() {
        return true;
    }

    SlashResult execute(SlashContext ctx);
}
