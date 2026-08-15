package com.tepeu.agent.tool;

import com.tepeu.service.WorkspacePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Agent 工具 — 在当前绑定工作区目录执行 Shell 命令。
 * 工具名 {@code run_command}；完整输出按会话写入 {@link CommandOutputStore}，
 * 模型即时回传有上限，超出部分用 {@link ReadOutputTool} 续读。
 */
@Component
public class RunCommandTool extends WorkspaceBoundTool {

    private static final Logger log = LoggerFactory.getLogger(RunCommandTool.class);

    /** 默认超时（秒） */
    static final int DEFAULT_TIMEOUT_SEC = 60;
    /** 最长超时（秒） */
    static final int MAX_TIMEOUT_SEC = 300;
    /** 回传模型的输出上限（字节，合并 stdout+stderr） */
    static final int MAX_OUTPUT_BYTES = 32 * 1024;
    /** 采集并缓存的上限（字节），供 read_output 续读 */
    static final int MAX_STORE_BYTES = CommandOutputStore.MAX_STORED_CHARS;

    /** 明显危险操作的简单拦截（不追求完美，只挡常见误伤） */
    private static final Pattern DENY = Pattern.compile(
            "(?i)(\\bformat\\s+[a-z]:|\\bshutdown\\b|\\bdel\\s+/[sf]|\\brmdir\\s+/s|\\brm\\s+-rf\\s+/|"
                    + ":\\s*\\(\\s*\\)\\s*\\{|\\bmkfs\\b|\\bdiskpart\\b)");

    private final CommandOutputStore outputStore;
    private final AtomicReference<String> activeSessionId = new AtomicReference<>();

    @Autowired
    public RunCommandTool(WorkspacePathResolver pathResolver, CommandOutputStore outputStore) {
        super(pathResolver);
        this.outputStore = outputStore;
    }

    /** 测试缝：固定 cwd */
    RunCommandTool(Path basePath) {
        this(basePath, new CommandOutputStore());
    }

    RunCommandTool(Path basePath, CommandOutputStore outputStore) {
        super(basePath);
        this.outputStore = outputStore;
    }

    /** 跨包单测工厂 */
    public static RunCommandTool forTests(Path basePath) {
        return new RunCommandTool(basePath);
    }

    /** 供单测注入共享输出缓存 */
    public static RunCommandTool forTests(Path basePath, CommandOutputStore outputStore) {
        return new RunCommandTool(basePath, outputStore);
    }

    /** 本轮对话绑定会话 id，并清空该会话旧输出 */
    public void bindSession(String sessionId) {
        activeSessionId.set(sessionId);
        outputStore.clear(sessionId);
    }

    public void unbindSession() {
        activeSessionId.set(null);
    }

    @Tool(name = "run_command", description =
            "Run a shell command inside the current workspace directory and return exit code plus output. "
                    + "Use after writing or editing code to compile, test, or run programs "
                    + "(e.g. `mvn test`, `npm test`, `python main.py`, `dir`). "
                    + "Working directory is the workspace root. Immediate output is capped at ~32KB; "
                    + "use read_output to fetch later slices of longer output.")
    public String runCommand(
            @ToolParam(description = "Shell command to run in the workspace root.")
            String command,
            @ToolParam(description = "Optional timeout in seconds (default 60, max 300). Omit to use 60.")
            Integer timeoutSec) {
        if (command == null || command.isBlank()) {
            return storeAndReturnToModel("", "ERROR: empty command");
        }
        String trimmed = command.trim();
        if (DENY.matcher(trimmed).find()) {
            return storeAndReturnToModel(trimmed, "ERROR: command blocked by safety policy");
        }
        int timeout = timeoutSec == null ? DEFAULT_TIMEOUT_SEC : timeoutSec;
        if (timeout < 1) timeout = 1;
        if (timeout > MAX_TIMEOUT_SEC) timeout = MAX_TIMEOUT_SEC;

        Path cwd = currentBasePath();
        ProcessBuilder pb = new ProcessBuilder(shellArgs(trimmed));
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);

        log.info("run_command cwd={} timeout={}s cmd={}", cwd, timeout, abbreviate(trimmed, 120));
        Process process = null;
        try {
            process = pb.start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Thread reader = drainAsync(process.getInputStream(), buf, MAX_STORE_BYTES);
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(2000);
                String partial = decodeOutput(buf.toByteArray());
                String full = "ERROR: timed out after " + timeout + "s\n--- output so far ---\n" + partial;
                return storeAndReturnToModel(trimmed, full);
            }
            reader.join(5000);
            int exit = process.exitValue();
            String fullOut = decodeOutput(buf.toByteArray());
            boolean storeCapped = buf.size() >= MAX_STORE_BYTES;
            StringBuilder stored = new StringBuilder();
            stored.append("exit_code=").append(exit).append('\n');
            if (storeCapped) {
                stored.append("[stored output truncated to ").append(MAX_STORE_BYTES).append(" bytes]\n");
            }
            stored.append(fullOut);
            return storeAndReturnToModel(trimmed, stored.toString());
        } catch (IOException e) {
            return storeAndReturnToModel(trimmed, "ERROR: failed to start process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return storeAndReturnToModel(trimmed, "ERROR: interrupted while waiting for command");
        }
    }

    /**
     * 完整结果写入会话缓存；返回给模型的副本可能截断，并提示用 read_output。
     */
    private String storeAndReturnToModel(String command, String fullResult) {
        // 优先 ThreadLocal（Hook 执行线程）；否则用 bindSession 的回退值（单测）
        String sid = CommandOutputStore.currentKey();
        if (CommandOutputStore.ANON_KEY.equals(sid) && activeSessionId.get() != null) {
            sid = activeSessionId.get();
        }
        outputStore.put(sid, command, fullResult);
        if (fullResult.length() <= MAX_OUTPUT_BYTES) {
            return fullResult;
        }
        String head = fullResult.substring(0, MAX_OUTPUT_BYTES);
        return head + "\n...[truncated for model: showing first " + MAX_OUTPUT_BYTES
                + " of " + fullResult.length()
                + " chars; call read_output with offset=" + MAX_OUTPUT_BYTES + " for more]";
    }

    /** Windows → cmd /c；其它 → sh -c */
    static String[] shellArgs(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", command};
        }
        return new String[]{"sh", "-c", command};
    }

    private static Thread drainAsync(InputStream in, ByteArrayOutputStream buf, int maxBytes) {
        Thread t = new Thread(() -> {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    synchronized (buf) {
                        int room = maxBytes - buf.size();
                        if (room <= 0) {
                            continue;
                        }
                        buf.write(chunk, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
            }
        }, "tepeu-shell-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String decodeOutput(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "(no output)";
        }
        // Windows 控制台常见 GBK；先试 UTF-8，含大量替换符则回退系统默认
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (countReplacement(utf8) > bytes.length / 20) {
            return new String(bytes, Charset.defaultCharset());
        }
        return utf8;
    }

    private static int countReplacement(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '�') n++;
        }
        return n;
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
