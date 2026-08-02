package com.tepeu.agent.hook;

import com.tepeu.agent.tool.ToolEventEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** HookingToolCallback：拦截 / 放行 / 会话授权后放行。 */
class HookingToolCallbackTest {

    private ApprovalStore store;
    private DangerousToolHook hook;
    private List<Map<String, Object>> events;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore(0L);
        hook = new DangerousToolHook();
        events = new ArrayList<>();
    }

    @Test
    void runCommand_withoutGrant_blocksAndEmitsApproval() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "OK: ran";
        });
        ToolEventEmitter emitter = events::add;
        HookingToolCallback wrapped = new HookingToolCallback(raw, hook, store, "sess-1", emitter);

        String result = wrapped.call("{\"command\":\"dir\"}");

        assertEquals(0, calls.get());
        assertTrue(result.startsWith("ERROR: APPROVAL_REQUIRED"));
        assertEquals(1, events.size());
        assertEquals("tool_approval_required", events.get(0).get("type"));
        assertEquals("run_command", events.get(0).get("tool"));
        assertEquals("shell", events.get(0).get("toolKind"));
    }

    @Test
    void runCommand_retrySamePending_doesNotEmitTwice() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "OK";
        });
        ToolEventEmitter emitter = events::add;
        HookingToolCallback wrapped = new HookingToolCallback(raw, hook, store, "sess-1", emitter);

        wrapped.call("{\"command\":\"dir\"}");
        wrapped.call("{\"command\":\"dir\"}");

        assertEquals(0, calls.get());
        assertEquals(1, events.size(), "同会话同工具只应弹出一条审批");
    }

    @Test
    void writeFile_allowedWithoutApproval() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("write_file", () -> {
            calls.incrementAndGet();
            return "OK: wrote";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-1", ToolEventEmitter.NOOP);
        assertEquals("OK: wrote", wrapped.call("{\"path\":\"/a.txt\"}"));
        assertEquals(1, calls.get());
    }

    @Test
    void runCommand_afterApprove_executesSameArgsOnly() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "OK: ran";
        });
        String args = "{\"command\":\"dir\"}";
        ApprovalStore.Approval pending = store.createPending("sess-1", "run_command", args);
        store.decide(pending.id(), true);

        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-1", ToolEventEmitter.NOOP);
        assertEquals("OK: ran", wrapped.call(args));
        assertEquals(1, calls.get());

        // 不同参数仍需再批
        String blocked = wrapped.call("{\"command\":\"echo x\"}");
        assertEquals(1, calls.get());
        assertTrue(blocked.startsWith("ERROR: APPROVAL_REQUIRED"));
    }

    @Test
    void catastrophicCommand_deniedWithoutApproval() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "should-not-run";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-1", events::add);
        String result = wrapped.call("{\"command\":\"rm -rf /\"}");
        assertEquals(0, calls.get());
        assertTrue(result.startsWith("ERROR: DENIED"));
        assertTrue(events.stream().anyMatch(e -> "tool_denied".equals(e.get("type"))));
    }

    @Test
    void listFiles_alwaysAllowed() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("list_files", () -> {
            calls.incrementAndGet();
            return "[]";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-1", ToolEventEmitter.NOOP);
        assertEquals("[]", wrapped.call("{}"));
        assertEquals(1, calls.get());
    }

    @Test
    void autonomousSession_skipsApprovalForRunCommand() {
        store.enableAutonomous("sess-auto");
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "OK";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-auto", events::add);
        assertEquals("OK", wrapped.call("{\"command\":\"dir\"}"));
        assertEquals(1, calls.get());
        assertTrue(events.isEmpty(), "自主会话不应弹出审批");
    }

    @Test
    void autonomousSession_stillRequiresApprovalForMcp() {
        store.enableAutonomous("sess-auto");
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("mcp_fs_write", () -> {
            calls.incrementAndGet();
            return "ok";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-auto", events::add);
        String blocked = wrapped.call("{}");
        assertEquals(0, calls.get());
        assertTrue(blocked.startsWith("ERROR: APPROVAL_REQUIRED"));
        assertFalse(events.isEmpty(), "MCP 在自主会话仍应申请审批");
    }

    @Test
    void autonomousSession_stillRequiresApprovalForDeleteFile() {
        store.enableAutonomous("sess-auto");
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("delete_file", () -> {
            calls.incrementAndGet();
            return "deleted";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, store, "sess-auto", events::add);
        String blocked = wrapped.call("{\"path\":\"/a.txt\"}");
        assertEquals(0, calls.get());
        assertTrue(blocked.startsWith("ERROR: APPROVAL_REQUIRED"));
        assertFalse(events.isEmpty(), "delete_file 在自主会话仍应申请审批");
    }

    @Test
    void runCommand_withWait_executesAfterAsyncApprove() throws Exception {
        ApprovalStore waiting = new ApprovalStore(30L);
        AtomicInteger calls = new AtomicInteger();
        ToolCallback raw = stub("run_command", () -> {
            calls.incrementAndGet();
            return "OK";
        });
        HookingToolCallback wrapped =
                new HookingToolCallback(raw, hook, waiting, "sess-wait", events::add);

        Thread approver = Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    var p = waiting.findPending("sess-wait", "run_command");
                    if (p.isPresent()) {
                        waiting.decide(p.get().id(), true);
                        return;
                    }
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals("OK", wrapped.call("{\"command\":\"dir\"}"));
        assertEquals(1, calls.get());
        approver.join(5_000);
    }

    private static ToolCallback stub(String name, java.util.function.Supplier<String> body) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return new ToolDefinition() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public String description() {
                        return name;
                    }

                    @Override
                    public String inputSchema() {
                        return "{}";
                    }
                };
            }

            @Override
            public String call(String toolInput) {
                return body.get();
            }
        };
    }
}
