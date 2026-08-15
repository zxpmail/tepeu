package com.tepeu.agent.mcp;

import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.agent.hook.DangerousToolHook;
import com.tepeu.agent.hook.HookingToolCallback;
import com.tepeu.agent.tool.ToolEventEmitter;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MCP 桥接：禁用/空连接、命名空间、状态诊断、Hook 审批。 */
class McpToolBridgeTest {

    @Test
    void disabled_returnsEmpty() {
        @SuppressWarnings("unchecked")
        ObjectProvider<List<McpSyncClient>> clients = mock(ObjectProvider.class);
        McpToolBridge bridge = new McpToolBridge(clients, false, 0L);
        assertEquals(0, bridge.callbacks().length);
        assertFalse(bridge.isClientEnabled());
        assertTrue(bridge.status().note().contains("未启用"));
    }

    @Test
    void enabled_noClients_warnsInStatus() {
        @SuppressWarnings("unchecked")
        ObjectProvider<List<McpSyncClient>> clients = mock(ObjectProvider.class);
        when(clients.getIfAvailable(any())).thenReturn(List.of());
        McpToolBridge bridge = new McpToolBridge(clients, true, 0L);
        assertTrue(bridge.isClientEnabled());
        assertEquals(0, bridge.callbacks().length);
        McpToolBridge.StatusSnapshot s = bridge.status();
        assertEquals(0, s.clientCount());
        assertNotNull(s.warning());
        assertTrue(s.note().contains("无可用连接"));
    }

    @Test
    void sanitizeAndNamespaceHelpers() {
        assertEquals("file_system", McpToolBridge.sanitizeToken("File System!"));
        assertEquals("write", McpToolBridge.stripMcpPrefix("mcp_write"));
        assertEquals("write", McpToolBridge.stripMcpPrefix("mcp_mcp_write"));

        @SuppressWarnings("unchecked")
        ObjectProvider<List<McpSyncClient>> clients = mock(ObjectProvider.class);
        McpSyncClient c = mock(McpSyncClient.class);
        when(c.getServerInfo()).thenReturn(new io.modelcontextprotocol.spec.McpSchema.Implementation("File System", "1.0"));
        var counters = new LinkedHashMap<String, Integer>();
        assertEquals("file_system", McpToolBridge.uniqueServerKey(c, 0, counters));
        assertEquals("file_system2", McpToolBridge.uniqueServerKey(c, 1, counters));
    }

    @Test
    void mcpNamedTool_blockedByHookUntilGranted() {
        AtomicInteger calls = new AtomicInteger();
        ToolCallback mcpTool = stub("mcp_fs_external_write", () -> {
            calls.incrementAndGet();
            return "ok";
        });
        ApprovalStore store = new ApprovalStore(0L);
        HookingToolCallback wrapped = new HookingToolCallback(
                mcpTool, new DangerousToolHook(), store, "sess-1", ToolEventEmitter.NOOP);

        String blocked = wrapped.call("{}");
        assertEquals(0, calls.get());
        assertTrue(blocked.startsWith("ERROR: APPROVAL_REQUIRED"));

        var pending = store.createPending("sess-1", "mcp_fs_external_write", "{}");
        store.decide(pending.id(), true);
        assertTrue(store.hasSessionGrant("sess-1", "mcp_fs_external_write", "{}"));
        assertEquals("ok", wrapped.call("{}"));
        assertEquals(1, calls.get());
    }

    private static ToolCallback stub(String name, Supplier<String> body) {
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
