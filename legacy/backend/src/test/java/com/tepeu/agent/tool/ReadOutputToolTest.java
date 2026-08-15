package com.tepeu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReadOutputTool} + {@link CommandOutputStore} with {@link RunCommandTool}.
 */
class ReadOutputToolTest {

    @TempDir
    Path tempDir;

    private CommandOutputStore store;
    private RunCommandTool runCommand;
    private ReadOutputTool readOutput;

    @BeforeEach
    void setUp() {
        store = new CommandOutputStore();
        runCommand = RunCommandTool.forTests(tempDir, store);
        readOutput = new ReadOutputTool(store);
        runCommand.bindSession("sess-test");
        readOutput.bindSession("sess-test");
    }

    @Test
    void readOutput_beforeCommand_errors() {
        assertTrue(readOutput.readOutput(0, 100).startsWith("ERROR: no command output"));
    }

    @Test
    void readOutput_afterCommand_returnsSlice() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String cmd = os.contains("win") ? "echo hello-output" : "echo hello-output";
        String run = runCommand.runCommand(cmd, 15);
        assertTrue(run.toLowerCase(Locale.ROOT).contains("hello-output"), run);

        String slice = readOutput.readOutput(0, 200);
        assertTrue(slice.contains("command="), slice);
        assertTrue(slice.toLowerCase(Locale.ROOT).contains("hello-output"), slice);
        assertTrue(slice.contains("offset=0"), slice);
    }

    @Test
    void readOutput_canReadBeyondModelCap() {
        // 模拟：缓存超过模型 32KB 回传上限，read_output 从偏移处续读
        StringBuilder big = new StringBuilder("exit_code=0\n");
        big.append("HEAD_MARKER=");
        big.append("x".repeat(40_000));
        big.append("TAIL_MARKER");
        store.put("sess-test", "gen", big.toString());

        String mid = readOutput.readOutput(35_000, 8_000);
        assertTrue(mid.contains("TAIL_MARKER") || mid.contains("xxxxx"), mid);
        assertTrue(mid.contains("total=" + big.length()) || mid.contains("total="), mid);
    }

    @Test
    void sessions_doNotLeak() {
        store.put("sess-a", "a", "aaa");
        store.put("sess-b", "b", "bbb");
        readOutput.bindSession("sess-a");
        assertTrue(readOutput.readOutput(0, 10).contains("aaa"));
        readOutput.bindSession("sess-b");
        assertTrue(readOutput.readOutput(0, 10).contains("bbb"));
    }

    @Test
    void readOutput_offsetBeyond_errors() {
        store.put("sess-test", "echo", "short");
        String out = readOutput.readOutput(100, 50);
        assertTrue(out.startsWith("ERROR: offset"), out);
    }

    @Test
    void readOutput_respectsMaxChars() {
        store.put("sess-test", "cmd", "abcdefghijklmnopqrstuvwxyz");
        String out = readOutput.readOutput(0, 5);
        assertTrue(out.contains("---\n"), out);
        assertTrue(out.contains("abcde"), out);
        assertFalse(out.contains("abcdef"), out);
        assertTrue(out.contains("next offset=5"), out);
    }

    @Test
    void toolName_read_output_isRegistered() {
        ToolCallback[] cbs = ToolCallbacks.from(readOutput);
        Set<String> names = Arrays.stream(cbs)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("read_output"), "read_output must be registered: " + names);
    }
}
