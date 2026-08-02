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
 * Unit tests for {@link ReadFileTool} — reading file content in a workspace.
 */
class ReadFileToolTest {

    @TempDir
    Path tempDir;

    private ReadFileTool tools;

    @BeforeEach
    void setUp() throws IOException {
        tools = new ReadFileTool(tempDir);
        Files.writeString(tempDir.resolve("notes.txt"), "hello world");
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("a.txt"), "inside sub");
    }

    @Test
    void readFile_returnsFileContent() {
        assertEquals("hello world", tools.readFile("/notes.txt"));
        assertEquals("inside sub", tools.readFile("/sub/a.txt"));
    }

    @Test
    void readFile_missingFile_returnsErrorString() {
        assertTrue(tools.readFile("/nope.txt").startsWith("ERROR: file not found"));
    }

    @Test
    void readFile_directory_returnsErrorString() {
        assertTrue(tools.readFile("/sub").startsWith("ERROR: file not found"));
    }

    @Test
    void readFile_traversalEscape_returnsErrorString() {
        assertTrue(tools.readFile("/../etc/passwd").startsWith("ERROR: path traversal denied"));
    }

    @Test
    void readFile_overCap_isTruncatedWithMarker() throws IOException {
        char[] chars = new char[ReadFileTool.MAX_READ_BYTES + 50];
        Arrays.fill(chars, 'x');
        Files.writeString(tempDir.resolve("big.txt"), new String(chars));

        String content = tools.readFile("/big.txt");
        assertTrue(content.endsWith(" bytes]"), "truncation marker must be present");
        assertTrue(content.contains("[truncated"), content);
    }

    @Test
    void readFile_underCap_returnedInFull() {
        String content = tools.readFile("/notes.txt");
        assertEquals("hello world", content);
        assertFalse(content.contains("[truncated"));
    }

    @Test
    void toolCallbacks_areDiscoverableWithExpectedNames() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(callbacks)
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("read_file"), "read_file must be registered: " + names);
        assertEquals(1, names.size(), "only one tool should be exposed: " + names);
    }

    @Test
    void toolCallbacks_haveNonEmptyDescriptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        for (ToolCallback c : callbacks) {
            assertFalse(c.getToolDefinition().description().isBlank());
        }
    }
}
