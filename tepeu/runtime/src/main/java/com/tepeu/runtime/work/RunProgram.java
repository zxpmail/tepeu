package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 跑命令。不经过系统外壳，等程序自己结束。
 * 退出码是 0 才算成功。超过时限记超时，然后不再等。
 */
public final class RunProgram implements Job {

    /** 最多等这么久。 */
    public static final Duration WAIT = Duration.ofSeconds(5);

    private final Duration wait;

    /** 用已经定死的 5 秒。 */
    public RunProgram() {
        this(WAIT);
    }

    /** 测试可以换一个更短的时限。外面交进来只能用 5 秒那一个。 */
    RunProgram(Duration wait) {
        if (wait == null || wait.isZero() || wait.isNegative()) {
            throw new IllegalArgumentException("跑命令必须有时限");
        }
        this.wait = wait;
    }

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.RUN;
    }

    /** 命令外壳的文件名。不启动这些。 */
    private static final Set<String> SHELLS = Set.of(
            "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh", "pwsh.exe", "bash", "sh", "wsl", "wsl.exe");

    /** 启动目标程序。参数是交来的非空行，每行一个，不交给外壳。 */
    @Override
    public Reply run(Attempt attempt) {
        if (shell(attempt.target())) {
            return new Reply(ResultKind.失败, null, "不经过命令外壳");
        }
        List<String> command = new ArrayList<>();
        command.add(attempt.target());
        if (!attempt.payload().isBlank()) {
            for (String line : attempt.payload().split("\\R", -1)) {
                if (!line.isBlank()) {
                    command.add(line);
                }
            }
        }
        return runCommand(command, wait, workingDirectory(attempt.directory(), attempt.target()));
    }

    /** 文件名是命令外壳就不启动。 */
    private static boolean shell(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        try {
            String name = Path.of(target).getFileName().toString().toLowerCase(Locale.ROOT);
            return SHELLS.contains(name);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 限制是目录就在里面跑。限制是文件，就在它所在的目录里跑。 */
    private static Path workingDirectory(String limit, String target) {
        Path fromLimit = existingDirectory(limit);
        if (fromLimit != null) {
            return fromLimit;
        }
        return existingDirectory(target);
    }

    /** 已有的目录用它本身。已有的文件用它所在的目录。 */
    private static Path existingDirectory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(raw);
            if (Files.isDirectory(path)) {
                return path;
            }
            Path parent = path.getParent();
            if (parent != null && Files.isDirectory(parent) && Files.exists(path)) {
                return parent;
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }

    /** 按给定的程序和时限等它结束。测试用来确认超时这条路。 */
    Reply runCommand(List<String> command, Duration limit) {
        return runCommand(command, limit, null);
    }

    /** 有工作目录就在那个目录里启动。 */
    private Reply runCommand(List<String> command, Duration limit, Path directory) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (directory != null) {
                builder.directory(directory.toFile());
            }
            process = builder.start();
            boolean finished = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                return new Reply(ResultKind.超时, null, "");
            }
            if (process.exitValue() == 0) {
                return Reply.success(output);
            }
            return new Reply(ResultKind.失败, "RUN_FAILED", "");
        } catch (Exception ex) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Reply(ResultKind.失败, "RUN_FAILED", "");
        }
    }
}
