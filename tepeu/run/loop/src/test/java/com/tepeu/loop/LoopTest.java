package com.tepeu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.dispatch.Dispatch;
import com.tepeu.execution.Workspace;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.llm.gateway.LlmGateway;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.session.Session;
import com.tepeu.session.SessionStore;
import com.tepeu.session.persist.PersistedSessionStore;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoopTest {

    @TempDir
    Path dir;

    @Test
    void fullTurnWithToolRound() throws Exception {
        Path work = Files.createDirectories(dir.resolve("work"));
        Files.writeString(work.resolve("a.txt"), "data");
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = openSession(persist);
            session.inbox().enqueue("hi");
            ArrayDeque<String> replies = new ArrayDeque<>(
                    List.of("@tool execution.fs.read path=a.txt", "done"));
            LlmGateway gateway = new LlmGateway(v -> SyscallResult.success(replies.poll(), new Usage(1, 1)));
            Workspace workspace = new Workspace(work);
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), null);
            dispatch.register(Loop.LLM_GENERATE, (ctx, s) -> gateway.generate(session.log()));
            dispatch.register(Workspace.FS_READ, workspace::read);
            Loop loop = new Loop(dispatch);

            assertTrue(loop.runOnce(session));

            List<SessionEvent> log = session.log().readAll();
            assertEquals(List.of(
                            SessionEventType.USER_MESSAGE,
                            SessionEventType.TOOL_CALL,
                            SessionEventType.TOOL_RESULT,
                            SessionEventType.ASSISTANT_MESSAGE),
                    log.stream().map(SessionEvent::type).toList());
            assertEquals("data", log.get(2).body());
            assertEquals("done", log.get(3).body());
            assertEquals("idle", session.registers().get("loop.state").orElseThrow());
            assertTrue(Loop.complete(session.log()));
            assertEquals(2, session.ledger().readAll().size());
            assertEquals(Loop.LLM_GENERATE, session.ledger().readAll().get(0).syscallName());
        }
    }

    @Test
    void deniedToolRecordedAndTurnCompletes() throws Exception {
        Path work = Files.createDirectories(dir.resolve("work"));
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = openSession(persist);
            session.inbox().enqueue("write something");
            ArrayDeque<String> replies = new ArrayDeque<>(
                    List.of("@tool execution.fs.write path=x.txt content=v", "gave up"));
            LlmGateway gateway = new LlmGateway(v -> SyscallResult.success(replies.poll()));
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), null);
            dispatch.register(Loop.LLM_GENERATE, (ctx, s) -> gateway.generate(session.log()));
            Loop loop = new Loop(dispatch);

            assertTrue(loop.runOnce(session));

            List<SessionEvent> log = session.log().readAll();
            SessionEvent toolResult = log.get(2);
            assertEquals(SessionEventType.TOOL_RESULT, toolResult.type());
            assertEquals(Dispatch.DENIED, toolResult.attr("error").orElseThrow());
            assertFalse(Files.exists(work.resolve("x.txt")));
            assertEquals("gave up", log.get(3).body());
            assertTrue(Loop.complete(session.log()));
        }
    }

    @Test
    void roundCapStopsTheLoop() {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = openSession(persist);
            session.inbox().enqueue("probe forever");
            ArrayDeque<String> replies = new ArrayDeque<>();
            for (int i = 0; i < Loop.MAX_TOOL_ROUNDS + 1; i++) {
                replies.add("@tool execution.sandbox.probe");
            }
            LlmGateway gateway = new LlmGateway(v -> SyscallResult.success(replies.poll()));
            Workspace workspace = new Workspace(dir);
            Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), null);
            dispatch.register(Loop.LLM_GENERATE, (ctx, s) -> gateway.generate(session.log()));
            dispatch.register(Workspace.SANDBOX_PROBE, workspace::probe);
            Loop loop = new Loop(dispatch);

            assertTrue(loop.runOnce(session));

            List<SessionEvent> log = session.log().readAll();
            assertEquals(Loop.MAX_TOOL_ROUNDS,
                    log.stream().filter(e -> e.type() == SessionEventType.TOOL_CALL).count());
            assertEquals(SessionEventType.REASONING, log.get(log.size() - 1).type());
            assertFalse(Loop.complete(session.log()));
            assertEquals("idle", session.registers().get("loop.state").orElseThrow());
        }
    }

    @Test
    void emptyQueueReturnsFalse() {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Session session = openSession(persist);
            Loop loop = new Loop(new Dispatch(new DefaultRuleMatrix(), null));
            assertFalse(loop.runOnce(session));
            assertTrue(session.log().readAll().isEmpty());
        }
    }

    private Session openSession(SqlitePersist persist) {
        SessionStore store = new PersistedSessionStore(persist);
        return store.open(SessionStore.DEFAULT, new Principal(new PrincipalId("u")), new WorkspaceId("ws"));
    }
}
