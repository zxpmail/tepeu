package com.tepeu.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.llm.gateway.LlmMessage;
import com.tepeu.llm.gateway.Role;
import com.tepeu.load.Assembly.Wired;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssemblyTest {

    @TempDir
    Path dir;

    @Test
    void wireRegistersAllNames() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Wired wired = Assembly.wire(persist, v -> SyscallResult.success("ok"),
                    Assembly.SYSTEM_PROMPT, dir);
            assertEquals(List.of(
                    "execution.fs.read",
                    "execution.fs.write",
                    "execution.proc.spawn",
                    "execution.sandbox.probe",
                    "llm.generate"), wired.dispatch().names());
        }
    }

    @Test
    void turnRunsEndToEndWithSystemPrompt() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            AtomicReference<List<LlmMessage>> seen = new AtomicReference<>();
            Wired wired = Assembly.wire(persist, visible -> {
                seen.set(visible);
                return SyscallResult.success("done", new Usage(1, 1));
            }, Assembly.SYSTEM_PROMPT, dir);
            wired.session().inbox().enqueue("hi");

            assertTrue(wired.loop().runOnce(wired.session()));

            assertEquals(new LlmMessage(Role.SYSTEM, Assembly.SYSTEM_PROMPT), seen.get().get(0));
            assertEquals("hi", seen.get().get(1).body());
            List<SessionEvent> log = wired.session().log().readAll();
            assertEquals(SessionEventType.ASSISTANT_MESSAGE, log.get(log.size() - 1).type());
            assertTrue(com.tepeu.loop.Loop.complete(wired.session().log()));
            assertEquals(1, wired.session().ledger().readAll().size());
        }
    }

    @Test
    void writeGoesThroughApprovalRoundTrip() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            ArrayDeque<String> replies = new ArrayDeque<>(List.of(
                    "@tool execution.fs.write path=x.txt;content=v",
                    "好的，等批准。",
                    "@tool execution.fs.write path=x.txt;content=v",
                    "写好了。"));
            Wired wired = Assembly.wire(persist, v -> SyscallResult.success(replies.poll()),
                    Assembly.SYSTEM_PROMPT, dir);

            wired.session().inbox().enqueue("make a file");
            assertTrue(wired.loop().runOnce(wired.session()));
            assertFalse(Files.exists(dir.resolve("x.txt")));
            String approvalId = wired.session().log().readAll().stream()
                    .filter(e -> e.type() == SessionEventType.TOOL_RESULT)
                    .filter(e -> e.attr("error").orElse("").equals("APPROVAL_REQUIRED"))
                    .map(SessionEvent::body)
                    .findFirst().orElseThrow();
            assertTrue(wired.commands().execute(wired.session(), "/status").contains(approvalId));
            assertTrue(wired.commands().execute(wired.session(), "/approve " + approvalId)
                    .startsWith("已批准"));

            wired.session().inbox().enqueue("retry");
            assertTrue(wired.loop().runOnce(wired.session()));
            assertTrue(Files.exists(dir.resolve("x.txt")));
            assertEquals("v", Files.readString(dir.resolve("x.txt")));
            assertTrue(com.tepeu.loop.Loop.complete(wired.session().log()));
        }
    }

    @Test
    void afterCompactLoopSeesOnlySummaryAndNewEvents() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            AtomicReference<List<LlmMessage>> seen = new AtomicReference<>();
            Wired wired = Assembly.wire(persist, visible -> {
                seen.set(visible);
                return SyscallResult.success("摘要内容", new Usage(1, 1));
            }, Assembly.SYSTEM_PROMPT, dir);

            wired.session().log().append(SessionEventType.USER_MESSAGE, "旧问题");
            wired.session().log().append(SessionEventType.ASSISTANT_MESSAGE, "旧答复");
            String out = wired.commands().execute(wired.session(), "/compact");
            assertTrue(out.contains("已压缩"));
            assertTrue(out.contains("压缩点 2"));

            wired.session().inbox().enqueue("新问题");
            assertTrue(wired.loop().runOnce(wired.session()));
            assertEquals(List.of(
                    new LlmMessage(Role.SYSTEM, Assembly.SYSTEM_PROMPT),
                    new LlmMessage(Role.SUMMARY, "摘要内容"),
                    new LlmMessage(Role.USER, "新问题")),
                    seen.get());

            List<SessionEvent> log = wired.session().log().readAll();
            assertEquals(SessionEventType.COMPACT, log.get(2).type());
            assertEquals("2", log.get(2).attr("compact.upTo").orElseThrow());
            assertTrue(log.stream().anyMatch(e -> "旧问题".equals(e.body())),
                    "账只追加：旧事件仍在，只是不再回放");
        }
    }
}
