package com.tepeu.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceTest {

    @TempDir
    Path dir;

    private final InvokeContext ctx = new InvokeContext(
            new Principal(new PrincipalId("u")),
            new WorkspaceId("ws"),
            new SessionId("default"));

    @Test
    void readWriteRoundtripCreatesParents() {
        Workspace ws = new Workspace(dir);
        assertTrue(ws.write(ctx, new Syscall(Workspace.FS_WRITE,
                Map.of("path", "sub/a.txt", "content", "hi"))).ok());
        SyscallResult r = ws.read(ctx, new Syscall(Workspace.FS_READ, Map.of("path", "sub/a.txt")));
        assertTrue(r.ok());
        assertEquals("hi", r.output());
    }

    @Test
    void escapeAndMissingPathRejected() {
        Workspace ws = new Workspace(dir);
        assertEquals(Workspace.PATH_OUTSIDE_WORKSPACE,
                ws.read(ctx, new Syscall(Workspace.FS_READ, Map.of("path", "../escape.txt")))
                        .errorCode().orElseThrow());
        assertEquals(Workspace.PATH_OUTSIDE_WORKSPACE,
                ws.read(ctx, new Syscall(Workspace.FS_READ, Map.of()))
                        .errorCode().orElseThrow());
        String outside = dir.getParent().resolve("elsewhere.txt").toString();
        assertEquals(Workspace.PATH_OUTSIDE_WORKSPACE,
                ws.write(ctx, new Syscall(Workspace.FS_WRITE,
                        Map.of("path", outside, "content", "x"))).errorCode().orElseThrow());
    }

    @Test
    void spawnCapturesOutputAndReportsExit() {
        Workspace ws = new Workspace(dir);
        SyscallResult r = ws.spawn(ctx, new Syscall(Workspace.PROC_SPAWN, Map.of("command", "echo hi")));
        assertTrue(r.ok());
        assertTrue(r.output().contains("hi"));
        SyscallResult f = ws.spawn(ctx, new Syscall(Workspace.PROC_SPAWN, Map.of("command", "exit 3")));
        assertFalse(f.ok());
        assertEquals("PROC_EXIT_3", f.errorCode().orElseThrow());
        assertEquals(Workspace.COMMAND_MISSING,
                ws.spawn(ctx, new Syscall(Workspace.PROC_SPAWN, Map.of())).errorCode().orElseThrow());
    }

    @Test
    void spawnTimeoutKillsProcess() {
        Workspace ws = new Workspace(dir, Duration.ofMillis(200));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        // pause / sleep 无孙进程：destroyForcibly 只杀直接子进程，孙进程会占住工作区目录
        SyscallResult r = ws.spawn(ctx, new Syscall(Workspace.PROC_SPAWN,
                Map.of("command", windows ? "pause" : "sleep 5")));
        assertFalse(r.ok());
        assertEquals(Workspace.PROC_TIMEOUT, r.errorCode().orElseThrow());
    }

    @Test
    void probeReportsIsolationHonestly() {
        Workspace ws = new Workspace(dir);
        SyscallResult r = ws.probe(ctx, new Syscall(Workspace.SANDBOX_PROBE, Map.of()));
        assertTrue(r.ok());
        assertTrue(r.output().contains("path containment"));
    }
}
