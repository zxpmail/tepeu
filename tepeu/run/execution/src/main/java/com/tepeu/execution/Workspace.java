package com.tepeu.execution;

import com.tepeu.identity.InvokeContext;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 工作区执行。名称与 policy 矩阵对齐：{@link #FS_READ} / {@link #FS_WRITE} /
 * {@link #PROC_SPAWN} / {@link #SANDBOX_PROBE}。
 * handler 级错误码与 dispatch 门码分家：{@link #PATH_OUTSIDE_WORKSPACE} / {@link #IO_ERROR} /
 * {@link #COMMAND_MISSING} / {@link #PROC_TIMEOUT} / {@code PROC_EXIT_<n>}。
 * 方法签名与 dispatch.Handler 对齐，装配时直接做方法引用。
 */
public final class Workspace {

    public static final String FS_READ = "execution.fs.read";
    public static final String FS_WRITE = "execution.fs.write";
    public static final String PROC_SPAWN = "execution.proc.spawn";
    public static final String SANDBOX_PROBE = "execution.sandbox.probe";

    public static final String PATH_OUTSIDE_WORKSPACE = "PATH_OUTSIDE_WORKSPACE";
    public static final String IO_ERROR = "IO_ERROR";
    public static final String COMMAND_MISSING = "COMMAND_MISSING";
    public static final String PROC_TIMEOUT = "PROC_TIMEOUT";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Path root;
    private final Duration timeout;

    public Workspace(Path root) {
        this(root, DEFAULT_TIMEOUT);
    }

    Workspace(Path root, Duration timeout) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public Path root() {
        return root;
    }

    public SyscallResult read(InvokeContext ctx, Syscall syscall) {
        String path = syscall.args().get("path");
        Path target = contain(path);
        if (target == null) {
            return SyscallResult.failure(PATH_OUTSIDE_WORKSPACE, path == null ? "path missing" : path);
        }
        try {
            return SyscallResult.success(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return SyscallResult.failure(IO_ERROR, e.getClass().getSimpleName());
        }
    }

    public SyscallResult write(InvokeContext ctx, Syscall syscall) {
        String path = syscall.args().get("path");
        Path target = contain(path);
        if (target == null) {
            return SyscallResult.failure(PATH_OUTSIDE_WORKSPACE, path == null ? "path missing" : path);
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, syscall.args().getOrDefault("content", ""), StandardCharsets.UTF_8);
            return SyscallResult.success("written");
        } catch (IOException e) {
            return SyscallResult.failure(IO_ERROR, e.getClass().getSimpleName());
        }
    }

    public SyscallResult spawn(InvokeContext ctx, Syscall syscall) {
        String command = syscall.args().get("command");
        if (command == null || command.isBlank()) {
            return SyscallResult.failure(COMMAND_MISSING, "command missing");
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        List<String> argv = windows
                ? List.of("cmd", "/c", command)
                : List.of("sh", "-c", command);
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                return SyscallResult.failure(PROC_TIMEOUT, command);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.exitValue();
            return code == 0
                    ? SyscallResult.success(output)
                    : SyscallResult.failure("PROC_EXIT_" + code, output);
        } catch (IOException e) {
            return SyscallResult.failure(IO_ERROR, e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyscallResult.failure(IO_ERROR, "interrupted");
        }
    }

    public SyscallResult probe(InvokeContext ctx, Syscall syscall) {
        return SyscallResult.success("isolation: path containment only; no os-level sandbox (first cut)");
    }

    /** 路径圈禁。null/空白、绝对路径、越出 root 一律 null。 */
    private Path contain(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        Path target = root.resolve(relative).normalize();
        return target.startsWith(root) ? target : null;
    }
}
