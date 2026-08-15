package com.tepeu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WriteFileTool} — writing files in a workspace.
 */
class WriteFileToolTest {

    @TempDir
    Path tempDir;

    private WriteFileTool tools;

    @BeforeEach
    void setUp() {
        tools = new WriteFileTool(tempDir);
    }

    @Test
    void writeFile_createsAndOverwrites() throws Exception {
        String result = tools.writeFile("/out.txt", "hello-write");
        assertTrue(result.startsWith("OK:"), result);
        assertEquals("hello-write", Files.readString(tempDir.resolve("out.txt")));

        String again = tools.writeFile("/out.txt", "updated");
        assertTrue(again.startsWith("OK:"), again);
        assertEquals("updated", Files.readString(tempDir.resolve("out.txt")));
    }

    @Test
    void writeFile_missingParent_hallucinationError() {
        String result = tools.writeFile("/deep/nested/file.txt", "content");
        assertTrue(result.contains("HALLUCINATION"), result);
        assertFalse(Files.exists(tempDir.resolve("deep/nested/file.txt")));
    }

    @Test
    void writeFile_existingParent_ok() throws Exception {
        Files.createDirectories(tempDir.resolve("deep/nested"));
        String result = tools.writeFile("/deep/nested/file.txt", "content");
        assertTrue(result.startsWith("OK:"), result);
        assertEquals("content", Files.readString(tempDir.resolve("deep/nested/file.txt")));
    }

    @Test
    void writeFile_rejectsTraversal() {
        String result = tools.writeFile("../outside.txt", "x");
        assertTrue(result.startsWith("ERROR:"), result);
    }

    @Test
    void writeFile_rejectsOversize() {
        StringBuilder sb = new StringBuilder(WriteFileTool.MAX_WRITE_BYTES + 1);
        for (int i = 0; i < WriteFileTool.MAX_WRITE_BYTES + 1; i++) sb.append('x');
        String result = tools.writeFile("/big.txt", sb.toString());
        assertTrue(result.startsWith("ERROR: content too large"), result);
    }

    @Test
    void writeFile_nullContent_writesEmpty() throws Exception {
        String result = tools.writeFile("/empty.txt", null);
        assertTrue(result.startsWith("OK:"), result);
        assertEquals("", Files.readString(tempDir.resolve("empty.txt")));
    }

    @Test
    void toolCallbacks_areDiscoverableWithExpectedNames() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(callbacks)
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("write_file"), "write_file must be registered: " + names);
        assertEquals(1, names.size(), "only one tool should be exposed: " + names);
    }

    @Test
    void toolCallback_writeFile_invocableByName() {
        ToolCallback cb = Arrays.stream(ToolCallbacks.from(tools))
                .filter(c -> "write_file".equals(c.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String result = cb.call("{\"path\":\"/by-callback.txt\",\"content\":\"works\"}");
        // Spring AI ToolCallback may tack on extra output; check for OK marker inside
        assertTrue(result.contains("OK:") || result.startsWith("OK:"),
                "expected OK marker in: " + result);
    }
}
