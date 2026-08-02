package com.tepeu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SearchFileTool} — filename/content search in workspace.
 */
class SearchFileToolTest {

    @TempDir
    Path tempDir;

    private SearchFileTool tools;

    @BeforeEach
    void setUp() throws IOException {
        tools = SearchFileTool.forTests(tempDir);
        Files.writeString(tempDir.resolve("readme.md"), "hello tepeu agent");
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "class App { /* uniqueTokenXYZ */ }");
        Path skipped = tempDir.resolve("node_modules").resolve("pkg");
        Files.createDirectories(skipped);
        Files.writeString(skipped.resolve("secret.txt"), "uniqueTokenXYZ should be skipped");
    }

    @Test
    void searchFiles_byFileName() {
        String out = tools.searchFiles("readme", "/", 20);
        assertTrue(out.contains("[NAME] readme.md"), out);
    }

    @Test
    void searchFiles_byContent() {
        String out = tools.searchFiles("uniqueTokenXYZ", "/", 20);
        assertTrue(out.contains("[CONTENT] src/App.java") || out.contains("[CONTENT] src\\App.java"), out);
        assertFalse(out.contains("node_modules"), out);
    }

    @Test
    void searchFiles_emptyQuery() {
        assertTrue(tools.searchFiles("  ", "/", 10).startsWith("ERROR: empty"));
    }

    @Test
    void searchFiles_noMatches() {
        assertEquals("(no matches)", tools.searchFiles("zzz_no_such_thing", "/", 10));
    }

    @Test
    void searchFiles_pathTraversalDenied() {
        assertEquals("ERROR: path traversal denied", tools.searchFiles("x", "/../etc", 10));
    }

    @Test
    void toolName_search_files_isRegistered() {
        ToolCallback[] cbs = ToolCallbacks.from(tools);
        Set<String> names = Arrays.stream(cbs)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
        assertTrue(names.contains("search_files"), "search_files must be registered: " + names);
    }
}
