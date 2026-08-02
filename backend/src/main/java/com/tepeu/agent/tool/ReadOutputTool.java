package com.tepeu.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 工具 — 按偏移读取本会话最近一次 {@code run_command} 的完整输出。
 * 工具名 {@code read_output}；与 {@link RunCommandTool} 共用 {@link CommandOutputStore}。
 */
@Component
public class ReadOutputTool {

    /** 单次回传上限（字符） */
    static final int DEFAULT_MAX_CHARS = 8 * 1024;
    static final int HARD_MAX_CHARS = 32 * 1024;

    private final CommandOutputStore outputStore;
    /** 当前对话会话 id（工具回调可能在别的线程） */
    private final AtomicReference<String> activeSessionId = new AtomicReference<>();

    public ReadOutputTool(CommandOutputStore outputStore) {
        this.outputStore = outputStore;
    }

    /** 本轮对话绑定会话，与 {@link RunCommandTool#bindSession} 成对。 */
    public void bindSession(String sessionId) {
        activeSessionId.set(sessionId);
    }

    public void unbindSession() {
        activeSessionId.set(null);
    }

    @Tool(name = "read_output", description =
            "Read a slice of the most recent run_command output by character offset. "
            + "run_command returns at most ~32KB to the model; fuller output (up to ~256KB) is stored "
            + "for this tool. Use when the previous result said more is available via read_output.")
    public String readOutput(
            @ToolParam(description = "Character offset into the stored output (0 = start).")
            Integer offset,
            @ToolParam(description = "Optional max characters to return (default 8192, max 32768).")
            Integer maxChars) {
        String sid = CommandOutputStore.currentKey();
        if (CommandOutputStore.ANON_KEY.equals(sid) && activeSessionId.get() != null) {
            sid = activeSessionId.get();
        }
        CommandOutputStore.Snapshot snap = outputStore.get(sid);
        if (snap == null) {
            return "ERROR: no command output available; run_command first";
        }
        int off = offset == null ? 0 : offset;
        if (off < 0) off = 0;
        int max = maxChars == null ? DEFAULT_MAX_CHARS : maxChars;
        if (max < 1) max = 1;
        if (max > HARD_MAX_CHARS) max = HARD_MAX_CHARS;

        String out = snap.output();
        if (off >= out.length()) {
            return "ERROR: offset " + off + " beyond output length " + out.length()
                    + " (command: " + abbreviate(snap.command(), 80) + ")";
        }
        int end = Math.min(out.length(), off + max);
        String slice = out.substring(off, end);
        StringBuilder sb = new StringBuilder();
        sb.append("command=").append(abbreviate(snap.command(), 120)).append('\n');
        sb.append("offset=").append(off).append(" length=").append(slice.length())
                .append(" total=").append(out.length()).append('\n');
        if (end < out.length()) {
            sb.append("[more available; next offset=").append(end).append("]\n");
        }
        sb.append("---\n");
        sb.append(slice);
        return sb.toString();
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
