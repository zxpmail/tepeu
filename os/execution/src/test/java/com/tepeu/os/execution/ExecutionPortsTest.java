package com.tepeu.os.execution;

import com.tepeu.os.execution.local.OsJails;
import com.tepeu.os.execution.local.FsReadHandler;
import com.tepeu.os.execution.local.FsWriteHandler;
import com.tepeu.os.execution.local.ProcSpawnHandler;
import com.tepeu.os.execution.local.SandboxProbeHandler;
import com.tepeu.os.execution.local.WorkspaceJail;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPortsTest {

    @TempDir
    Path workspace;

    @Test
    void jailRejectsEscapeAndAllowsRelativeReadWrite() throws Exception {
        SandboxPolicy sandbox = new SandboxPolicy(workspace, OsJails.detect());
        TurnContext ctx = turn();
        SyscallResult escape = new FsReadHandler(sandbox).handle(ctx,
                new Syscall(ExecutionNames.FS_READ, Map.of("path", "../outside.txt")));
        assertFalse(escape.ok());
        assertEquals("JAIL", escape.errorCode().orElse(""));

        SyscallResult written = new FsWriteHandler(sandbox).handle(ctx,
                new Syscall(ExecutionNames.FS_WRITE, Map.of("path", "note.txt", "content", "hi")));
        assertTrue(written.ok(), written.output());
        assertEquals("hi", Files.readString(workspace.resolve("note.txt")));

        SyscallResult read = new FsReadHandler(sandbox).handle(ctx,
                new Syscall(ExecutionNames.FS_READ, Map.of("path", "note.txt")));
        assertTrue(read.ok());
        assertEquals("hi", read.output());
    }

    @Test
    void jailRejectsAbsolutePath() {
        String abs = workspace.resolve("note.txt").toAbsolutePath().toString();
        assertTrue(WorkspaceJail.resolve(workspace, abs).isEmpty());
        assertTrue(WorkspaceJail.resolve(workspace, "C:\\Windows\\notepad.exe").isEmpty());
        assertTrue(WorkspaceJail.resolve(workspace, "/etc/passwd").isEmpty());
    }

    @Test
    void spawnIsJailedOrFailsVisibleAndProbeStaysPartial() throws Exception {
        SandboxPolicy sandbox = new SandboxPolicy(workspace, OsJails.detect());
        TurnContext ctx = turn();
        ProcSpawnHandler spawn = new ProcSpawnHandler(sandbox);
        if (sandbox.jail().canSpawn()) {
            String script = writeProbeScript(workspace);
            SyscallResult ok = spawn.handle(ctx,
                    new Syscall(ExecutionNames.PROC_SPAWN, Map.of("path", script)));
            assertTrue(ok.ok(), ok.output());
            assertTrue(ok.output().contains("hello-jail"), ok.output());
            try (var leftover = Files.list(workspace)) {
                assertTrue(leftover.noneMatch(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("proc-") && name.endsWith(".out");
                }));
            }
            SyscallResult escape = spawn.handle(ctx,
                    new Syscall(ExecutionNames.PROC_SPAWN, Map.of("path", "../x")));
            assertFalse(escape.ok());
            assertEquals("JAIL", escape.errorCode().orElse(""));
        } else {
            SyscallResult refused = spawn.handle(ctx,
                    new Syscall(ExecutionNames.PROC_SPAWN, Map.of("argv", "echo")));
            assertFalse(refused.ok());
            assertEquals("SANDBOX_UNAVAILABLE", refused.errorCode().orElse(""));
            assertTrue(refused.output().contains("no silent"));
        }

        SyscallResult probe = new SandboxProbeHandler(sandbox).handle(ctx,
                new Syscall(ExecutionNames.SANDBOX_PROBE, Map.of()));
        assertTrue(probe.ok());
        assertTrue(probe.output().contains("isolation=partial"));
        assertFalse(probe.output().contains("isolation=full"));
        assertNotEquals(SandboxIsolation.FULL, sandbox.isolation());
        if (sandbox.jail().canSpawn()) {
            assertTrue(probe.output().contains("proc=available"));
            assertTrue(probe.output().contains("mechanism=" + sandbox.mechanism()));
        } else {
            assertTrue(probe.output().contains("proc=unavailable"));
        }
    }

    static String writeProbeScript(Path workspace) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            Path bat = workspace.resolve("probe.bat");
            Files.writeString(bat, "@echo off\r\necho hello-jail\r\n");
            return "probe.bat";
        }
        Path sh = workspace.resolve("probe.sh");
        Files.writeString(sh, "#!/bin/sh\necho hello-jail\n");
        try {
            Files.setPosixFilePermissions(sh, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            // FAT/Windows-on-WSL oddities
        }
        return "probe.sh";
    }

    private static TurnContext turn() {
        return new TurnContext(
                Principal.personal(new PrincipalId("ex-user")),
                Namespace.ofWorkspace(new WorkspaceId("ex-ws")),
                new SessionId("ex-s"),
                Optional.empty());
    }
}
