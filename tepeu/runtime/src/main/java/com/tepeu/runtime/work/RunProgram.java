package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /** 启动目标程序。不拆命令行，不交给外壳。 */
    @Override
    public Reply run(Attempt attempt) {
        return runCommand(List.of(attempt.target()), wait);
    }

    /** 按给定的程序和时限等它结束。测试用来确认超时这条路。 */
    Reply runCommand(List<String> command, Duration limit) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
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
