package com.tepeu.controller;

import com.tepeu.agent.mcp.McpToolBridge;
import com.tepeu.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 状态与资源只读 API（Spec M2.2）。
 * 关联：McpToolBridge、服务商设置页 MCP 区块。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpToolBridge mcpToolBridge;

    public McpController(McpToolBridge mcpToolBridge) {
        this.mcpToolBridge = mcpToolBridge;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        McpToolBridge.StatusSnapshot snap = mcpToolBridge.status();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", snap.enabled());
        data.put("clientCount", snap.clientCount());
        data.put("toolCount", snap.tools().size());
        data.put("tools", snap.tools());
        data.put("resourceCount", snap.resources().size());
        data.put("resources", snap.resources());
        data.put("warning", snap.warning());
        data.put("note", snap.note());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 强制刷新工具/资源缓存。 */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh() {
        mcpToolBridge.invalidateCache();
        return status();
    }

    /** 按 URI 读取 MCP 资源正文（文本或二进制占位说明）。 */
    @PostMapping("/resources/read")
    public ResponseEntity<ApiResponse<?>> readResource(@RequestBody Map<String, String> body) {
        try {
            String uri = body == null ? null : body.get("uri");
            String content = mcpToolBridge.readResource(uri);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uri", uri);
            data.put("content", content);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409)
                    .body(ApiResponse.error("MCP_UNAVAILABLE", e.getMessage()));
        }
    }
}
