package com.tepeu.agent.tool;

import com.tepeu.agent.mcp.McpToolBridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 工具名 → toolKind 分类，供 SSE、Hook、前端按类别处理。
 * 关联：ToolEventEmittingCallback、DangerousToolHook、HookingToolCallback。
 */
public final class ToolKinds {

    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();
    static {
        BY_NAME.put("list_files", "file_list");
        BY_NAME.put("read_file", "file_read");
        BY_NAME.put("write_file", "file_write");
        BY_NAME.put("delete_file", "file_delete");
        BY_NAME.put("search_files", "file_search");
        BY_NAME.put("run_command", "shell");
        BY_NAME.put("read_output", "shell_output");
        // 宿主通道（REST / 终端）合成工具名
        BY_NAME.put("rest_write_file", "file_write");
        BY_NAME.put("rest_upload_file", "file_write");
        BY_NAME.put("rest_restore_file", "file_write");
        // REST 删除与写同级：工作区沙箱内免批；Agent delete_file 仍为 file_delete 需批
        BY_NAME.put("rest_delete_file", "file_rest");
        BY_NAME.put("terminal_shell", "shell");
    }

    private static final Set<String> MCP_PREFIXES = Set.of(McpToolBridge.NAME_PREFIX);

    /** 明确免批的 toolKind（工作区沙箱读写/REST 删除与输出续读） */
    public static final Set<String> SAFE_KINDS = Set.of(
            "file_list", "file_read", "file_write", "file_search", "file_rest", "shell_output");

    /** 需用户批准的已知高危类别 */
    public static final Set<String> NEED_APPROVAL_KINDS = Set.of("shell", "mcp");

    private ToolKinds() {}

    /** 按工具名解析分类；未知返回 {@code other}。 */
    public static String of(String toolName) {
        if (toolName == null) return "unknown";
        String mapped = BY_NAME.get(toolName);
        if (mapped != null) return mapped;
        for (String prefix : MCP_PREFIXES) {
            if (toolName.startsWith(prefix)) return "mcp";
        }
        return "other";
    }

    /**
     * 是否默认需要审批。
     * fail-closed：未登记类别（other/unknown）一律需批；仅 SAFE_KINDS 免批。
     */
    public static boolean needsApproval(String toolKind) {
        if (toolKind == null || toolKind.isBlank()) {
            return true;
        }
        if (SAFE_KINDS.contains(toolKind)) {
            return false;
        }
        return true;
    }
}
