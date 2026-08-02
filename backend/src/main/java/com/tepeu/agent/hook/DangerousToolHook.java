package com.tepeu.agent.hook;

import com.tepeu.agent.tool.ToolKinds;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 按 {@link ToolKinds} 审批高危类别；灾难性 shell 命令直接 DENY。
 * <p>{@code shell}/{@code mcp}/未知工具需批准；工作区 {@code file_write} 与只读类免批。
 * Spec §7.2 / M2.3 / M2.2。
 */
@Component
public class DangerousToolHook implements ToolHook {

    /** 明显灾难性命令：不提供审批，直接拒绝 */
    private static final Pattern CATASTROPHIC = Pattern.compile(
            "(?i)(?:"
                    + "rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+)?/(?:\\s|$)"
                    + "|rm\\s+-rf\\s+/"
                    + "|del\\s+/[fs]\\s+"
                    + "|format\\s+[a-z]:\\s*"
                    + "|Remove-Item\\s+.*-Recurse.*[C-Z]:\\\\"
                    + "|mkfs\\."
                    + "|dd\\s+if=.+of=/dev/"
                    + ")");

    /** 终端只读/无害命令免批 */
    private static final Pattern SAFE_TERMINAL = Pattern.compile(
            "(?i)^\\s*(dir|cd|cls|type|echo|date|time|whoami|hostname|ver|help|pwd)\\b");

    @Override
    public Verdict evaluate(String toolName, String argsJson) {
        if (toolName == null) {
            return Verdict.NEED_APPROVAL;
        }
        if (("run_command".equals(toolName) || "terminal_shell".equals(toolName))
                && isCatastrophic(argsJson)) {
            return Verdict.DENY;
        }
        if ("terminal_shell".equals(toolName) && isSafeTerminal(argsJson)) {
            return Verdict.ALLOW;
        }
        String kind = ToolKinds.of(toolName);
        if (ToolKinds.needsApproval(kind)) {
            return Verdict.NEED_APPROVAL;
        }
        return Verdict.ALLOW;
    }

    static boolean isSafeTerminal(String argsJson) {
        if (argsJson == null) return false;
        // 粗提取 "command":"..."
        int i = argsJson.indexOf("\"command\"");
        if (i < 0) {
            return SAFE_TERMINAL.matcher(argsJson.trim()).find();
        }
        int colon = argsJson.indexOf(':', i);
        int q1 = argsJson.indexOf('"', colon + 1);
        int q2 = argsJson.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return false;
        String cmd = argsJson.substring(q1 + 1, q2);
        return SAFE_TERMINAL.matcher(cmd).find();
    }

    static boolean isCatastrophic(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return false;
        }
        String lower = argsJson.toLowerCase(Locale.ROOT);
        // 从 JSON 中粗提取 command 字段文本即可
        return CATASTROPHIC.matcher(argsJson).find()
                || CATASTROPHIC.matcher(lower).find();
    }
}
