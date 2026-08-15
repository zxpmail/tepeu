package com.tepeu.agent.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 高危工具规则矩阵。 */
class DangerousToolHookTest {

    private final DangerousToolHook hook = new DangerousToolHook();

    @Test
    void writeFile_allowedInWorkspaceSandbox() {
        assertEquals(ToolHook.Verdict.ALLOW, hook.evaluate("write_file", "{\"path\":\"/a\"}"));
    }

    @Test
    void runCommand_needsApproval() {
        assertEquals(ToolHook.Verdict.NEED_APPROVAL, hook.evaluate("run_command", "{\"command\":\"dir\"}"));
    }

    @Test
    void catastrophicShell_denied() {
        assertEquals(ToolHook.Verdict.DENY,
                hook.evaluate("run_command", "{\"command\":\"rm -rf /\"}"));
        assertEquals(ToolHook.Verdict.DENY,
                hook.evaluate("run_command", "{\"command\":\"format C:\"}"));
    }

    @Test
    void readOnlyTools_allowed() {
        assertEquals(ToolHook.Verdict.ALLOW, hook.evaluate("list_files", "{}"));
        assertEquals(ToolHook.Verdict.ALLOW, hook.evaluate("read_file", "{\"path\":\"/a\"}"));
        assertEquals(ToolHook.Verdict.ALLOW, hook.evaluate("search_files", "{\"query\":\"foo\"}"));
        assertEquals(ToolHook.Verdict.ALLOW, hook.evaluate("read_output", "{\"offset\":0}"));
    }

    @Test
    void mcpPrefixedTools_needApproval() {
        assertEquals(ToolHook.Verdict.NEED_APPROVAL, hook.evaluate("mcp_search", "{}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL, hook.evaluate("mcp_write_file", "{}"));
    }

    @Test
    void unknownTools_needApproval_failClosed() {
        assertEquals(ToolHook.Verdict.NEED_APPROVAL, hook.evaluate("custom_tool", "{}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL, hook.evaluate(null, "{}"));
    }

    @Test
    void terminalShell_safeAllowed_elseNeedsApproval() {
        assertEquals(ToolHook.Verdict.ALLOW,
                hook.evaluate("terminal_shell", "{\"command\":\"dir\"}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"del x.txt\"}"));
        assertEquals(ToolHook.Verdict.DENY,
                hook.evaluate("terminal_shell", "{\"command\":\"format C:\"}"));
    }

    @Test
    void terminalShell_escapeIndicators_needApproval() {
        // 越出工作区特征：重定向写盘 / 路径穿越 / 盘符绝对路径 → 不因「无害命令」免批
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"echo hi > C:/evil.txt\"}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"type ..\\..\\secret.txt\"}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"dir C:\\\"}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"dir \\windows\"}"));
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("terminal_shell", "{\"command\":\"dir | findstr x\"}"));
        // 无越界特征的无害命令仍免批
        assertEquals(ToolHook.Verdict.ALLOW,
                hook.evaluate("terminal_shell", "{\"command\":\"echo hello\"}"));
        assertEquals(ToolHook.Verdict.ALLOW,
                hook.evaluate("terminal_shell", "{\"command\":\"dir workspace\"}"));
    }

    @Test
    void restDelete_andWrite_allowedInSandbox() {
        assertEquals(ToolHook.Verdict.ALLOW,
                hook.evaluate("rest_delete_file", "{\"path\":\"/a\"}"));
        assertEquals(ToolHook.Verdict.ALLOW,
                hook.evaluate("rest_write_file", "{\"path\":\"/a\"}"));
    }

    @Test
    void deleteFile_needsApproval() {
        assertEquals(ToolHook.Verdict.NEED_APPROVAL,
                hook.evaluate("delete_file", "{\"path\":\"/a.txt\"}"));
    }
}
