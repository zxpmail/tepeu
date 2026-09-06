package com.tepeu.os.execution.local;

import com.tepeu.os.execution.*;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 工作区相对 {@code path} + OS jail。无 jail → {@code SANDBOX_UNAVAILABLE}，禁止静默直通。
 */
public final class ProcSpawnHandler implements SyscallHandler {

    private static final Logger LOG = System.getLogger(ProcSpawnHandler.class.getName());
    private static final String COMPONENT = "execution";
    private static final String CLASS_NAME = ProcSpawnHandler.class.getSimpleName();

    static final int TIMEOUT_SECONDS = 15;
    static final int OUTPUT_LIMIT = 8192;

    private final SandboxPolicy sandbox;

    public ProcSpawnHandler(SandboxPolicy sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        String sessionId = ctx.sessionId().value();
        if (!sandbox.jail().canSpawn()) {
            warn(sessionId, "refused=SANDBOX_UNAVAILABLE isolation=" + sandbox.isolation()
                    + " mechanism=" + sandbox.mechanism());
            return SyscallResult.failure("SANDBOX_UNAVAILABLE",
                    "proc spawn refused: isolation=" + sandbox.isolation()
                            + " mechanism=" + sandbox.mechanism()
                            + " (no OS jail; no silent unsandboxed exec)");
        }
        Path target = WorkspaceJail.resolve(sandbox.workspaceRoot(), syscall.args().get("path")).orElse(null);
        if (target == null) {
            warn(sessionId, "refused=JAIL reason=path");
            return SyscallResult.failure("JAIL", "spawn path must be workspace-relative");
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            warn(sessionId, "refused=JAIL reason=not-file");
            return SyscallResult.failure("JAIL", "spawn path is not a file in workspace");
        }
        List<String> extra = extraArgs(syscall.args().get("args"));
        Path workspace = sandbox.workspaceRoot().toAbsolutePath().normalize();
        try {
            return switch (sandbox.jail()) {
                case JOB_OBJECT -> spawnJob(sessionId, workspace, target, extra);
                case BWRAP -> spawnBwrap(sessionId, workspace, target, extra);
                case NONE -> {
                    warn(sessionId, "refused=SANDBOX_UNAVAILABLE reason=none");
                    yield SyscallResult.failure("SANDBOX_UNAVAILABLE", "jail kind none");
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warn(sessionId, "spawn interrupted");
            return SyscallResult.failure("SPAWN", "interrupted");
        } catch (IOException | RuntimeException e) {
            warn(sessionId, "spawn failed type=" + e.getClass().getSimpleName());
            return SyscallResult.failure("SPAWN", String.valueOf(e.getMessage()));
        }
    }

    private SyscallResult spawnJob(String sessionId, Path workspace, Path exe, List<String> extra)
            throws IOException {
        Map<String, String> env = SpawnEnv.filtered();
        List<String> argv = windowsArgv(exe, extra, env);
        Path out = Files.createTempFile(workspace, "proc-", ".out");
        try {
            WindowsJob.Spawned spawned = WindowsJob.run(workspace, argv, env, out, TIMEOUT_SECONDS, OUTPUT_LIMIT);
            if (spawned.timeout()) {
                warn(sessionId, "spawn timeout seconds=" + TIMEOUT_SECONDS);
                return SyscallResult.failure("TIMEOUT", "spawn exceeded " + TIMEOUT_SECONDS + "s");
            }
            if (spawned.exitCode() != 0) {
                warn(sessionId, "spawn exit=" + spawned.exitCode());
                return SyscallResult.failure("SPAWN", "exit=" + spawned.exitCode() + " " + spawned.output());
            }
            return SyscallResult.success(spawned.output());
        } finally {
            SpawnIo.deleteQuietly(out);
        }
    }

    private SyscallResult spawnBwrap(String sessionId, Path workspace, Path exe, List<String> extra)
            throws IOException, InterruptedException {
        Path out = Files.createTempFile(workspace, "proc-", ".out");
        Process proc = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(BwrapJail.command(workspace, exe, extra));
            pb.environment().clear();
            pb.environment().putAll(SpawnEnv.unixMinimal());
            pb.redirectErrorStream(true);
            pb.redirectOutput(out.toFile());
            proc = pb.start();
            return finish(sessionId, proc, out);
        } catch (InterruptedException e) {
            destroyQuietly(proc);
            throw e;
        } catch (IOException | RuntimeException e) {
            destroyQuietly(proc);
            throw e;
        } finally {
            SpawnIo.deleteQuietly(out);
        }
    }

    private static SyscallResult finish(String sessionId, Process proc, Path out)
            throws IOException, InterruptedException {
        boolean done = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!done) {
            proc.destroyForcibly();
            warn(sessionId, "spawn timeout seconds=" + TIMEOUT_SECONDS);
            return SyscallResult.failure("TIMEOUT", "spawn exceeded " + TIMEOUT_SECONDS + "s");
        }
        String output = SpawnIo.readCapped(out, OUTPUT_LIMIT);
        proc.getInputStream().close();
        proc.getErrorStream().close();
        proc.getOutputStream().close();
        if (proc.exitValue() != 0) {
            warn(sessionId, "spawn exit=" + proc.exitValue());
            return SyscallResult.failure("SPAWN", "exit=" + proc.exitValue() + " " + output);
        }
        return SyscallResult.success(output);
    }

    private static void warn(String sessionId, String message) {
        LOG.log(Level.WARNING, "component={0} class={1} session={2} {3}",
                COMPONENT, CLASS_NAME, sessionId, message);
    }

    private static void destroyQuietly(Process proc) {
        if (proc != null) {
            proc.destroyForcibly();
        }
    }

    private static List<String> windowsArgv(Path exe, List<String> extra, Map<String, String> env) {
        List<String> argv = new ArrayList<>();
        String path = exe.toAbsolutePath().toString();
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) {
            argv.add(SpawnEnv.comspec(env));
            argv.add("/c");
        }
        argv.add(path);
        argv.addAll(extra);
        return argv;
    }

    private static List<String> extraArgs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.strip().split("\\s+"));
    }
}
