package com.tepeu.controller;

import com.tepeu.agent.mcp.McpToolBridge;
import com.tepeu.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** McpController 状态与读资源错误映射。 */
class McpControllerTest {

    private McpToolBridge bridge;
    private McpController controller;

    @BeforeEach
    void setUp() {
        bridge = mock(McpToolBridge.class);
        controller = new McpController(bridge);
    }

    @Test
    void status_ok() {
        when(bridge.status()).thenReturn(new McpToolBridge.StatusSnapshot(
                false, 0, List.of(), List.of(), null, "未启用"));
        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.status();
        assertEquals(200, res.getStatusCode().value());
        assertEquals("OK", res.getBody().getCode());
        assertEquals(false, res.getBody().getData().get("enabled"));
    }

    @Test
    void refresh_invalidatesThenStatus() {
        when(bridge.status()).thenReturn(new McpToolBridge.StatusSnapshot(
                true, 0, List.of(), List.of(), "无连接", "已启用但无可用连接"));
        ResponseEntity<ApiResponse<Map<String, Object>>> res = controller.refresh();
        verify(bridge).invalidateCache();
        assertEquals(200, res.getStatusCode().value());
        assertEquals(0, res.getBody().getData().get("clientCount"));
    }

    @Test
    void readResource_unavailable_409() {
        when(bridge.readResource("x://a")).thenThrow(new IllegalStateException("MCP 客户端未启用"));
        ResponseEntity<ApiResponse<?>> res = controller.readResource(Map.of("uri", "x://a"));
        assertEquals(409, res.getStatusCode().value());
        assertEquals("MCP_UNAVAILABLE", res.getBody().getCode());
    }

    @Test
    void readResource_badUri_400() {
        when(bridge.readResource(null)).thenThrow(new IllegalArgumentException("uri 不能为空"));
        ResponseEntity<ApiResponse<?>> res = controller.readResource(Map.of());
        assertEquals(400, res.getStatusCode().value());
    }
}
