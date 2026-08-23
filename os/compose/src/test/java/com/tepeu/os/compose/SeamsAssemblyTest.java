package com.tepeu.os.compose;

import com.tepeu.os.execution.ExecutionNames;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.loop.CompactionWork;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.MaintenanceConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.policy.ApprovalRequiredException;
import com.tepeu.os.policy.PolicyDeniedException;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeamsAssemblyTest {

    @Test
    void defaultMatrixAllowsGenerateWithoutCallerAllow() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("mx-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("mx-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        session.inbox().enqueue("hi", Optional.of("user"));
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        TurnOutcome o = wired.loop().run(ctx, LoopConfig.of("fake-model"));
        assertTrue(o.completed(), o.detail());
    }

    @Test
    void writeAskThenSameTurnReplayAfterDecide() throws Exception {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("ap-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ap-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        Syscall write = new Syscall(ExecutionNames.FS_WRITE, Map.of("path", "a.txt", "content", "x"));
        ApprovalRequiredException asked = assertThrows(ApprovalRequiredException.class,
                () -> wired.bus().invoke(ctx, write));
        wired.approvals().decide(asked.approvalId(), true, "host");
        SyscallResult r = wired.bus().invoke(ctx, write);
        assertTrue(r.ok(), r.output());
        assertEquals("x", Files.readString(wired.workspace().resolve("a.txt")));
        assertThrows(ApprovalRequiredException.class, () -> wired.bus().invoke(ctx, write));
    }

    @Test
    void unknownDeniedAndProcFailsVisibleAfterApprove() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("ex-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ex-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        assertThrows(PolicyDeniedException.class,
                () -> wired.bus().invoke(ctx, new Syscall("echo", Map.of())));
        SyscallResult probe = wired.bus().invoke(ctx, new Syscall(ExecutionNames.SANDBOX_PROBE, Map.of()));
        assertTrue(probe.ok());
        assertTrue(probe.output().contains("isolation=partial"));
        assertFalse(probe.output().contains("isolation=full"));

        String script;
        try {
            script = writeProbeScript(wired.workspace());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Syscall spawn = new Syscall(ExecutionNames.PROC_SPAWN, Map.of("path", script));
        ApprovalRequiredException asked = assertThrows(ApprovalRequiredException.class,
                () -> wired.bus().invoke(ctx, spawn));
        wired.approvals().decide(asked.approvalId(), true, "host");
        SyscallResult r = wired.bus().invoke(ctx, spawn);
        if (probe.output().contains("proc=available")) {
            assertTrue(r.ok(), r.output());
            assertTrue(r.output().contains("hello-jail"), r.output());
        } else {
            assertFalse(r.ok());
            assertEquals("SANDBOX_UNAVAILABLE", r.errorCode().orElse(""));
        }
    }

    @Test
    void approveCommandDecidesAndAudits() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("ap-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ap-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        Syscall write = new Syscall(ExecutionNames.FS_WRITE, Map.of("path", "b.txt", "content", "y"));
        ApprovalRequiredException asked = assertThrows(ApprovalRequiredException.class,
                () -> wired.bus().invoke(ctx, write));
        var result = wired.commands().dispatch(ctx, session, "/approve " + asked.approvalId() + " allow");
        assertTrue(result.ok(), result.output());
        SyscallResult written = wired.bus().invoke(ctx, write);
        assertTrue(written.ok(), written.output());
        assertEquals(1, wired.audit().readAll().size());
        assertEquals("approve", wired.audit().readAll().get(0).action());
        assertTrue(session.log().readAll().isEmpty());
    }

    @Test
    void approveCommandRejectsOtherSession() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("ap-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("ap-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        Session other = wired.sessions().create(owner, ns, Optional.empty());
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        TurnContext otherCtx = new TurnContext(owner, ns, other.id(), Optional.empty());
        Syscall write = new Syscall(ExecutionNames.FS_WRITE, Map.of("path", "c.txt", "content", "z"));
        ApprovalRequiredException asked = assertThrows(ApprovalRequiredException.class,
                () -> wired.bus().invoke(ctx, write));
        var stolen = wired.commands().dispatch(otherCtx, other, "/approve " + asked.approvalId() + " allow");
        assertFalse(stolen.ok());
        assertTrue(stolen.output().contains("not in this session"), stolen.output());
        var ok = wired.commands().dispatch(ctx, session, "/approve " + asked.approvalId() + " allow");
        assertTrue(ok.ok(), ok.output());
    }

    @Test
    void compactThenGenerateDoesNotAssertion() {
        MemoryAssembly.Wired wired = MemoryAssembly.memory();
        Principal owner = Principal.personal(new PrincipalId("cp-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("cp-ws"));
        Session session = wired.sessions().create(owner, ns, Optional.empty());
        for (int i = 0; i < 6; i++) {
            session.log().append(SessionEventType.USER_MESSAGE, "m" + i, Map.of());
        }
        TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
        TurnOutcome o = wired.loop().maintain(ctx, MaintenanceConfig.of(Duration.ofSeconds(5)),
                new CompactionWork(wired.bus(), "fake-model", "anthropic", 2));
        assertTrue(o.detail().contains("MAINTENANCE_DONE"), o.detail());
        assertTrue(session.log().readAll().stream()
                .anyMatch(e -> e.type() == SessionEventType.COMPACTION_CHECKPOINT));
        assertEquals(3, session.logReplace().surface().size());
        assertTrue(session.ledger().readAll().size() >= 1);
        SyscallResult again = wired.bus().invoke(ctx,
                new Syscall("llm.generate", Map.of("model", "fake-model")));
        assertTrue(again.ok(), again.output());
        assertFalse("ASSERTION".equals(again.errorCode().orElse("")));
    }

    private static String writeProbeScript(Path workspace) throws Exception {
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
            // non-posix
        }
        return "probe.sh";
    }
}
