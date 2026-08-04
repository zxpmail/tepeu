package com.tepeu.agent.slash;

/**
 * Slash 命令执行结果。
 * {@code action} 可选，供前端本地处理：{@code compact} 清空本屏上下文等。
 */
public record SlashResult(String text, String action) {

    public static SlashResult text(String text) {
        return new SlashResult(text, null);
    }

    public static SlashResult of(String text, String action) {
        return new SlashResult(text, action);
    }
}
