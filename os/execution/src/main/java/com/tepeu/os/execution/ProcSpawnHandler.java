package com.tepeu.os.execution;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

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

    static final int TIMEOUT_SECONDS = 15;
    static final int OUTPUT_LIMIT = 8192;

    private final SandboxPolicy sandbox;

    public ProcSpawnHandler(SandboxPolicy sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        if (!sandbox.jail().canSpawn()) {
            return SyscallResult.failure("SANDBOX_UNAVAILABLE",
                    "proc spawn refused: isolation=" + sandbox.isolation()
                            + " mechanism=" + sandbox.mechanism()
                            + " (no OS jail; no silent unsandboxed exec)");
        }
        Path target = WorkspaceJail.resolve(sandbox.workspaceRoot(), syscall.args().get("path")).orElse(null);
        if (target == null) {
            return SyscallResult.failure("JAIL", "spawn path must be workspace-relative");
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return SyscallResult.failure("JAIL", "spawn path is not a file in workspace");
        }
        List<String> extra = extraArgs(syscall.args().get("args"));
        Path workspace = sandbox.workspaceRoot().toAbsolutePath().normalize();
        try {
            return switch (sandbox.jail()) {
                case JOB_OBJECT -> spawnJob(workspace, target, extra);
                case BWRAP -> spawnBwrap(workspace, target, extra);
                case NONE -> SyscallResult.failure("SANDBOX_UNAVAILABLE", "jail kind none");
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyscallResult.failure("SPAWN", "interrupted");
        } catch (IOException | RuntimeException e) {
            return SyscallResult.failure("SPAWN", String.valueOf(e.getMessage()));
        }
    }

    private SyscallResult spawnJob(Path workspace, Path exe, List<String> extra) throws IOException {
        Map<String, String> env = SpawnEnv.filtered();
        List<String> argv = windowsArgv(exe, extra, env);
        Path out = Files.createTempFile(workspace, "proc-", ".out");
        try {
            WindowsJob.Spawned spawned = WindowsJob.run(workspace, argv, env, out, TIMEOUT_SECONDS, OUTPUT_LIMIT);
            if (spawned.timeout()) {
                return SyscallResult.failure("TIMEOUT", "spawn exceeded " + TIMEOUT_SECONDS + "s");
            }
            if (spawned.exitCode() != 0) {
                return SyscallResult.failure("SPAWN", "exit=" + spawned.exitCode() + " " + spawned.output());
            }
            return SyscallResult.success(spawned.output());
        } finally {
            SpawnIo.deleteQuietly(out);
        }
    }

    private SyscallResult spawnBwrap(Path workspace, Path exe, List<String> extra)
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
            return finish(proc, out);
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

    private static SyscallResult finish(Process proc, Path out) throws IOException, InterruptedException {
        boolean done = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!done) {
            proc.destroyForcibly();
            return SyscallResult.failure("TIMEOUT", "spawn exceeded " + TIMEOUT_SECONDS + "s");
        }
        String output = SpawnIo.readCapped(out, OUTPUT_LIMIT);
        proc.getInputStream().close();
        proc.getErrorStream().close();
        proc.getOutputStream().close();
        if (proc.exitValue() != 0) {
            return SyscallResult.failure("SPAWN", "exit=" + proc.exitValue() + " " + output);
        }
        return SyscallResult.success(output);
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
