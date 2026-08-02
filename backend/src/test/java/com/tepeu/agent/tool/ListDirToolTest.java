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
 * Unit tests for {@link ListDirTool} — listing files in a workspace directory.
 */
class ListDirToolTest {

    @TempDir
    Path tempDir;

    private ListDirTool tools;

    @BeforeEach
    void setUp() throws IOException {
        tools = new ListDirTool(tempDir);
        Files.writeString(tempDir.resolve("notes.txt"), "hello world");
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("a.txt"), "inside sub");
        Files.createDirectories(tempDir.resolve("empty"));
    }

    @Test
    void resolveSafely_leadingSlashPath_staysInsideBase() {
        Path resolved = tools.resolveSafely("/notes.txt");
        assertNotNull(resolved);
        assertTrue(resolved.startsWith(tempDir));
        assertEquals(tempDir.resolve("notes.txt"), resolved);
    }

    @Test
    void resolveSafely_bareName_doesNotBecomeDotfile() {
        Path resolved = tools.resolveSafely("notes.txt");
        assertNotNull(resolved);
        assertEquals(tempDir.resolve("notes.txt"), resolved);
    }

    @Test
    void resolveSafely_root_returnsBase() {
        assertEquals(tempDir, tools.resolveSafely("/"));
    }

    @Test
    void resolveSafely_leadingSlashParentTraversal_isRejected() {
        assertNull(tools.resolveSafely("/../etc/passwd"));
        assertNull(tools.resolveSafely("/sub/../../etc/passwd"));
    }

    @Test
    void resolveSafely_nullPath_resolvesToBase() {
        assertEquals(tempDir, tools.resolveSafely(null));
    }

    @Test
    void listFiles_root_returnsDirectoriesFirstThenFiles() {
        String listing = tools.listFiles("/");
        int subIdx = listing.indexOf("[DIR]  sub");
        int emptyIdx = listing.indexOf("[DIR]  empty");
        int notesIdx = listing.indexOf("[FILE] notes.txt");
        assertTrue(subIdx >= 0, listing);
        assertTrue(emptyIdx >= 0, listing);
        assertTrue(notesIdx >= 0, listing);
        assertTrue(subIdx < notesIdx, "dirs must sort before files: " + listing);
        assertTrue(emptyIdx < notesIdx, "dirs must sort before files: " + listing);
    }

    @Test
    void listFiles_subdirectory_listsItsContents() {
        String listing = tools.listFiles("/sub");
        assertTrue(listing.contains("[FILE] a.txt"), listing);
        assertFalse(listing.contains("notes.txt"), "parent entries must not leak in: " + listing);
    }

    @Test
    void listFiles_emptyDirectory_reportsEmpty() {
        assertEquals("(empty directory)", tools.listFiles("/empty"));
    }

    @Test
    void listFiles_missingDirectory_returnsErrorString() {
        String result = tools.listFiles("/does/not/exist");
        assertTrue(result.startsWith("ERROR: directory not found"), result);
    }

    @Test
    void listFiles_traversalEscape_returnsErrorString() {
        assertTrue(tools.listFiles("/../etc").startsWith("ERROR: path traversal denied"));
    }

    @Test
    void toolCallbacks_areDiscoverableWithExpectedNames() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(callbacks)
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("list_files"), "list_files must be registered: " + names);
        assertEquals(1, names.size(), "only one tool should be exposed: " + names);
    }

    @Test
    void toolCallbacks_haveNonEmptyDescriptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        for (ToolCallback c : callbacks) {
            assertFalse(c.getToolDefinition().description().isBlank(),
                    "tool " + c.getToolDefinition().name() + " needs a description");
        }
    }

    @Test
    void toolCallback_listFiles_invocableByNameAndReturnsListing() {
        ToolCallback cb = Arrays.stream(ToolCallbacks.from(tools))
                .filter(c -> "list_files".equals(c.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String result = cb.call("{\"path\":\"/\"}");
        assertNotNull(result);
        assertTrue(result.contains("notes.txt") || result.equals("(empty directory)"), result);
    }
}
