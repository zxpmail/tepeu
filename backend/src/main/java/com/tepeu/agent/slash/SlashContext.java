package com.tepeu.agent.slash;

import java.util.List;

/**
 * Slash 命令执行上下文。
 */
public record SlashContext(
        String workspaceId,
        String sessionId,
        List<String> args) {

    public String arg(int index) {
        if (args == null || index < 0 || index >= args.size()) {
            return null;
        }
        return args.get(index);
    }

    public String argsJoined() {
        if (args == null || args.isEmpty()) {
            return "";
        }
        return String.join(" ", args);
    }
}
