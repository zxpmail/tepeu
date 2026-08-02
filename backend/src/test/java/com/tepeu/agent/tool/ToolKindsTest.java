package com.tepeu.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ToolKinds} 映射与审批类别。 */
class ToolKindsTest {

    @Test
    void of_mapsBuiltInTools() {
        assertEquals("file_list", ToolKinds.of("list_files"));
        assertEquals("file_read", ToolKinds.of("read_file"));
        assertEquals("file_write", ToolKinds.of("write_file"));
        assertEquals("file_delete", ToolKinds.of("delete_file"));
        assertEquals("file_rest", ToolKinds.of("rest_delete_file"));
        assertEquals("file_search", ToolKinds.of("search_files"));
        assertEquals("shell", ToolKinds.of("run_command"));
        assertEquals("shell_output", ToolKinds.of("read_output"));
    }

    @Test
    void of_mapsMcpPrefix() {
        assertEquals("mcp", ToolKinds.of("mcp_search"));
        assertEquals("mcp", ToolKinds.of("mcp_"));
    }

    @Test
    void of_unknownAndNull() {
        assertEquals("other", ToolKinds.of("custom_tool"));
        assertEquals("unknown", ToolKinds.of(null));
    }

    @Test
    void needsApproval_failClosedExceptSafeKinds() {
        assertTrue(ToolKinds.needsApproval("shell"));
        assertTrue(ToolKinds.needsApproval("mcp"));
        assertTrue(ToolKinds.needsApproval("file_delete"));
        assertTrue(ToolKinds.needsApproval("other"));
        assertTrue(ToolKinds.needsApproval("unknown"));
        assertTrue(ToolKinds.needsApproval(null));
        assertFalse(ToolKinds.needsApproval("shell_output"));
        assertFalse(ToolKinds.needsApproval("file_write"));
        assertFalse(ToolKinds.needsApproval("file_search"));
        assertFalse(ToolKinds.needsApproval("file_read"));
        assertFalse(ToolKinds.needsApproval("file_rest"));
    }
}
