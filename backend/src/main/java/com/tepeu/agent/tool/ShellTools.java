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
 * 智能体 Shell 工具 — 在当前绑定工作区目录执行命令（改代码后可编译/测试/运行）。
 * 关联：WorkspacePathResolver、AgentOrchestrator、ChatService、FileTools。
 *
 * <p>与人工 WebSocket 终端独立；Agent 不经终端面板，直接 ProcessBuilder。
 */
@Component
public class ShellTools {

    private static final Logger log = LoggerFactory.getLogger(ShellTools.class);

    /** 默认超时（秒） */
    static final int DEFAULT_TIMEOUT_SEC = 60;
    /** 最长超时（秒） */
    static final int MAX_TIMEOUT_SEC = 300;
    /** 回传模型的输出上限（字节，合并 stdout+stderr） */
    static final int MAX_OUTPUT_BYTES = 32 * 1024;

    /** 明显危险操作的简单拦截（不追求完美，只挡常见误伤） */
    private static final Pattern DENY = Pattern.compile(
            "(?i)(\\bformat\\s+[a-z]:|\\bshutdown\\b|\\bdel\\s+/[sf]|\\brmdir\\s+/s|\\brm\\s+-rf\\s+/|"
                    + ":\\s*\\(\\s*\\)\\s*\\{|\\bmkfs\\b|\\bdiskpart\\b)");

    private final WorkspacePathResolver pathResolver;
    private final Path fixedBasePath;
    private final AtomicReference<Path> activeBasePath = new AtomicReference<>();

    @Autowired
    public ShellTools(WorkspacePathResolver pathResolver) {
        this.pathResolver = pathResolver;
        this.fixedBasePath = null;
    }

    /** 测试缝：固定 cwd */
    ShellTools(Path basePath) {
        this.pathResolver = null;
        this.fixedBasePath = basePath.toAbsolutePath().normalize();
    }

    /** 跨包单测工厂 */
    public static ShellTools forTests(Path basePath) {
        return new ShellTools(basePath);
    }

    /** 与 FileTools 同步：本轮对话绑定工作区根目录 */
    public void bindWorkspace(String workspaceId) {
        if (fixedBasePath != null) {
            activeBasePath.set(fixedBasePath);
            return;
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            activeBasePath.set(null);
            log.warn("ShellTools.bindWorkspace: workspaceId 为空");
            return;
        }
        Path base = pathResolver.resolveBasePath(workspaceId);
        activeBasePath.set(base);
        log.debug("ShellTools 已绑定 workspaceId={} → {}", workspaceId, base);
    }

    public void unbindWorkspace() {
        activeBasePath.set(null);
    }

    Path currentBasePath() {
        if (fixedBasePath != null) {
            return fixedBasePath;
        }
        Path bound = activeBasePath.get();
        if (bound != null) {
            return bound;
        }
        log.warn("ShellTools 在未 bindWorkspace 时被调用，回退到默认工作区根目录");
        return pathResolver.resolveBasePath(null);
    }

    @Tool(name = "run_command", description =
            "Run a shell command inside the current workspace directory and return exit code plus output. "
                    + "Use after writing or editing code to compile, test, or run programs "
                    + "(e.g. `mvn test`, `npm test`, `python main.py`, `dir`). "
                    + "Working directory is the workspace root. Prefer short commands; output is capped.")
    public String runCommand(
            @ToolParam(description = "Shell command to run in the workspace root.")
            String command,
            @ToolParam(description = "Optional timeout in seconds (default 60, max 300). Omit to use 60.")
            Integer timeoutSec) {
        if (command == null || command.isBlank()) {
            return "ERROR: empty command";
        }
        String trimmed = command.trim();
        if (DENY.matcher(trimmed).find()) {
            return "ERROR: command blocked by safety policy";
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
            Thread reader = drainAsync(process.getInputStream(), buf);
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.join(2000);
                String partial = decodeOutput(buf.toByteArray());
                return "ERROR: timed out after " + timeout + "s\n--- output so far ---\n" + partial;
            }
            reader.join(5000);
            int exit = process.exitValue();
            String out = decodeOutput(buf.toByteArray());
            boolean truncated = buf.size() >= MAX_OUTPUT_BYTES;
            StringBuilder sb = new StringBuilder();
            sb.append("exit_code=").append(exit).append('\n');
            if (truncated) {
                sb.append("[output truncated to ").append(MAX_OUTPUT_BYTES).append(" bytes]\n");
            }
            sb.append(out);
            return sb.toString();
        } catch (IOException e) {
            return "ERROR: failed to start process: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "ERROR: interrupted while waiting for command";
        }
    }

    /** Windows → cmd /c；其它 → sh -c */
    static String[] shellArgs(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", command};
        }
        return new String[]{"sh", "-c", command};
    }

    private static Thread drainAsync(InputStream in, ByteArrayOutputStream buf) {
        Thread t = new Thread(() -> {
            byte[] chunk = new byte[4096];
            try {
                int n;
                while ((n = in.read(chunk)) >= 0) {
                    synchronized (buf) {
                        int room = MAX_OUTPUT_BYTES - buf.size();
                        if (room <= 0) {
                            // 读尽但不再写入，避免塞满管道
                            continue;
                        }
                        buf.write(chunk, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
                // 进程结束时流关闭属正常
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
            if (s.charAt(i) == '\uFFFD') n++;
        }
        return n;
    }

    private static String abbreviate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
