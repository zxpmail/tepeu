package com.tepeu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RunCommandTool} — shell command execution in workspace.
 */
class RunCommandToolTest {

    @TempDir
    Path tempDir;

    private RunCommandTool tools;

    @BeforeEach
    void setUp() {
        tools = new RunCommandTool(tempDir);
    }

    @Test
    void runCommand_echo_runsInWorkspaceCwd() throws Exception {
        Files.writeString(tempDir.resolve("marker.txt"), "here");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String cmd = os.contains("win") ? "type marker.txt" : "cat marker.txt";
        String out = tools.runCommand(cmd, 15);
        assertTrue(out.contains("exit_code=0"), out);
        assertTrue(out.contains("here"), out);
    }

    @Test
    void runCommand_blockedDangerousCommand() {
        String out = tools.runCommand("rmdir /s /q C:\\Windows", 5);
        assertTrue(out.startsWith("ERROR: command blocked"), out);
    }

    @Test
    void runCommand_emptyRejected() {
        assertTrue(tools.runCommand("  ", 5).startsWith("ERROR: empty"));
    }

    @Test
    void toolName_run_command_isRegistered() {
        ToolCallback[] cbs = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(cbs)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("run_command"), "run_command must be registered: " + names);
    }

    @Test
    void toolCallbacks_haveNonEmptyDescriptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        for (ToolCallback c : callbacks) {
            assertFalse(c.getToolDefinition().description().isBlank());
        }
    }
}
