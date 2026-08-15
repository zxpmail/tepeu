package com.tepeu.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** delete_file 工具单测。 */
class DeleteFileToolTest {

    @TempDir
    Path tempDir;

    private DeleteFileTool tools;

    @BeforeEach
    void setUp() {
        tools = DeleteFileTool.forTests(tempDir);
    }

    @Test
    void deleteFile_removesExisting() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "x");
        String result = tools.deleteFile("/a.txt");
        assertTrue(result.startsWith("OK:"), result);
        assertFalse(Files.exists(tempDir.resolve("a.txt")));
    }

    @Test
    void deleteFile_missing_reportsError() {
        String result = tools.deleteFile("/nope.txt");
        assertTrue(result.startsWith("ERROR:"), result);
    }

    @Test
    void deleteFile_rejectsDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("sub"));
        String result = tools.deleteFile("/sub");
        assertTrue(result.contains("directory"), result);
    }
}
