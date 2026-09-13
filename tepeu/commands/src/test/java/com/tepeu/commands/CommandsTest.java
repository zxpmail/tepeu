package com.tepeu.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.dispatch.Dispatch;
import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.policy.persist.PersistedApprovalStore;
import com.tepeu.session.Session;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionStore;
import com.tepeu.session.persist.PersistedSessionStore;
import com.tepeu.syscall.Syscall;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandsTest {

    @TempDir
    Path dir;

    private record Rig(Session session, PersistedApprovalStore approvals, Dispatch dispatch,
                       Commands commands, InvokeContext ctx, SqlitePersist persist) implements AutoCloseable {
        @Override
        public void close() {
            persist.close();
        }
    }

    private Rig rig() {
        SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"));
        Session session = new PersistedSessionStore(persist).open(
                SessionStore.DEFAULT, new Principal(new PrincipalId("u")), new WorkspaceId("ws"));
        PersistedApprovalStore approvals = new PersistedApprovalStore(persist);
        Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), approvals);
        return new Rig(session, approvals, dispatch,
                new Commands(approvals, dispatch),
                new InvokeContext(session.owner(), session.workspace(), session.id()),
                persist);
    }

    @Test
    void helpAndUnknownAndNonSlash() {
        try (Rig r = rig()) {
            assertTrue(r.commands().execute(r.session(), "/help").contains("/approve"));
            assertTrue(r.commands().execute(r.session(), "/nope").contains("未知命令"));
            assertTrue(r.commands().execute(r.session(), "你好").contains("/help"));
        }
    }

    @Test
    void approveRejectsForeignMissingAndBlank() {
        try (Rig r = rig()) {
            InvokeContext other = new InvokeContext(
                    new Principal(new PrincipalId("u")),
                    new WorkspaceId("ws"),
                    new SessionId("other"));
            String foreign = r.approvals().ask(other, new Syscall("execution.fs.write", Map.of("path", "a.txt")));
            assertTrue(r.commands().execute(r.session(), "/approve " + foreign).contains("只许本会话"));
            assertFalse(r.approvals().get(foreign).orElseThrow().decided());

            assertTrue(r.commands().execute(r.session(), "/approve ap-999").contains("没有这条审批"));
            assertTrue(r.commands().execute(r.session(), "/approve").contains("用法"));
        }
    }

    @Test
    void approveDecidesOnceAndAudits() {
        try (Rig r = rig()) {
            String id = r.approvals().ask(r.ctx(), new Syscall("execution.fs.write", Map.of("path", "a.txt")));
            String out = r.commands().execute(r.session(), "/approve " + id);
            assertTrue(out.contains("已批准"));
            assertEquals(Optional.of(true), r.approvals().get(id).orElseThrow().allow());
            assertEquals(1, r.session().audit().readAll().size());
            assertTrue(r.commands().execute(r.session(), "/approve " + id).contains("已决策过"));
        }
    }

    @Test
    void statusShowsBooksAndPendingWithoutBodies() {
        try (Rig r = rig()) {
            r.session().log().append(SessionEventType.USER_MESSAGE, "secret body");
            r.session().ledger().record("llm.generate", new Usage(2, 3));
            String id = r.approvals().ask(r.ctx(), new Syscall("execution.fs.write", Map.of("path", "a.txt")));

            String out = r.commands().execute(r.session(), "/status");
            assertTrue(out.contains("USER_MESSAGE=1"));
            assertTrue(out.contains("5 tokens"));
            assertTrue(out.contains("循环 idle"));
            assertTrue(out.contains(id));
            assertTrue(out.contains("execution.fs.write"));
            assertFalse(out.contains("secret body"));
        }
    }

    @Test
    void btwOnlyWhenIdleAndNeverWritesLog() {
        try (Rig r = rig()) {
            r.dispatch().register("llm.generate", (c, s) -> SyscallResult.success("旁答", new Usage(1, 1)));
            r.session().registers().put("loop.state", "running");
            assertTrue(r.commands().execute(r.session(), "/btw 问我").contains("稍后再试"));

            r.session().registers().put("loop.state", "idle");
            assertEquals("旁答", r.commands().execute(r.session(), "/btw 问我"));
            assertEquals(1, r.session().ledger().readAll().size());
            assertTrue(r.session().log().readAll().isEmpty());
            assertTrue(r.commands().execute(r.session(), "/btw").contains("用法"));
        }
    }

    @Test
    void btwSurfacesDispatchFailure() {
        try (Rig r = rig()) {
            assertTrue(r.commands().execute(r.session(), "/btw hi").contains("NOT_FOUND"));
        }
    }
}
