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
 * Focused unit tests for {@link FileTools} — no Spring context, no LLM. Covers:
 * <ul>
 *   <li>The path-traversal guard (reused verbatim from {@code FileController}): {@code ..} escapes,
 *       absolute paths, and null all resolve safely or are rejected.</li>
 *   <li>{@code list_files} correctness: names, {@code [DIR]}/{[FILE]} tags, deterministic ordering,
 *       empty-dir marker, missing-dir error string (no throw).</li>
 *   <li>{@code read_file} correctness: content, the 8KB truncation marker, missing-file error
 *       string, and that directories are rejected as not-a-file.</li>
 *   <li>That the {@code @Tool} methods are discoverable by {@link ToolCallbacks#from(Object...)} with
 *       the exact tool names Spring AI will register — proving the wiring without a network call.</li>
 * </ul>
 * All filesystem state lives under a JUnit {@link TempDir}; the real {@code FileTools} bean uses
 * {@code <user.dir>/workspace} but the resolution logic is identical via the package-private
 * {@link FileTools#FileTools(Path)} test seam.
 */
class FileToolsTest {

    @TempDir
    Path tempDir;

    private FileTools tools;

    @BeforeEach
    void setUp() throws IOException {
        tools = new FileTools(tempDir);
        // Files live at the workspace root. Note: the path-traversal guard does
        // basePath.resolve("." + path), so callers use LEADING-SLASH paths (the FileController
        // contract, e.g. "/notes.txt" -> "./notes.txt" -> <root>/notes.txt). A bare "notes.txt"
        // would resolve to a dotfile ".notes.txt" — that's FileController's existing quirk, which
        // we reuse verbatim rather than "fix".
        Files.writeString(tempDir.resolve("notes.txt"), "hello world");
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("a.txt"), "inside sub");
        Files.createDirectories(tempDir.resolve("empty"));
    }

    // --- path-traversal guard (reused verbatim from FileController) ---------------------
    // FileController: target = basePath.resolve("." + path).normalize(); reject if !startsWith(base).

    @Test
    void resolveSafely_leadingSlashPath_staysInsideBase() {
        // The documented contract: "/notes.txt" -> "./notes.txt" -> <root>/notes.txt
        Path resolved = tools.resolveSafely("/notes.txt");
        assertNotNull(resolved);
        assertTrue(resolved.startsWith(tempDir), "resolved path must stay inside basePath");
        assertEquals(tempDir.resolve("notes.txt"), resolved);
    }

    @Test
    void resolveSafely_bareName_doesNotBecomeDotfile() {
        // 以前 "." + "notes.txt" = ".notes.txt"；现在会补上前导 /
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
        // "/../..." is the realistic escape vector: "." + "/../etc/passwd" = "./../etc/passwd",
        // which normalizes ABOVE basePath -> caught by startsWith. This is the form a tool-calling
        // model would actually emit to escape (matching FileController's leading-slash contract).
        assertNull(tools.resolveSafely("/../etc/passwd"), "escape above basePath must be rejected");
        assertNull(tools.resolveSafely("/sub/../../etc/passwd"));
    }

    @Test
    void resolveSafely_nullPath_resolvesToBase() {
        assertEquals(tempDir, tools.resolveSafely(null));
    }

    @Test
    void readFile_traversalEscape_returnsErrorString_doesNotThrow() {
        // Leading-slash escape attempt: must surface as an error string, never throw into the stream.
        String result = tools.readFile("/../etc/passwd");
        assertTrue(result.startsWith("ERROR: path traversal denied"), result);
    }

    // --- list_files ---------------------------------------------------------------------

    @Test
    void listFiles_root_returnsDirectoriesFirstThenFiles() {
        String listing = tools.listFiles("/");
        // empty/, sub/ are dirs (first), notes.txt is a file (after). Order is deterministic.
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

    // --- read_file ----------------------------------------------------------------------

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
        // Reading a directory is not a file read; surface as an error string, not an exception.
        assertTrue(tools.readFile("/sub").startsWith("ERROR: file not found"));
    }

    @Test
    void readFile_overCap_isTruncatedWithMarker() throws IOException {
        // Write a file just over the 8KB cap.
        char[] chars = new char[FileTools.MAX_READ_BYTES + 50];
        Arrays.fill(chars, 'x');
        Files.writeString(tempDir.resolve("big.txt"), new String(chars));

        String content = tools.readFile("/big.txt");
        assertTrue(content.endsWith(" bytes]"), "truncation marker must be present: tail=" +
                content.substring(Math.max(0, content.length() - 80)));
        assertTrue(content.contains("[truncated"), content);
        // The returned head is exactly MAX_READ_BYTES chars (plus the marker line).
        long headLen = content.lines().findFirst().orElse("").length();
        assertEquals(FileTools.MAX_READ_BYTES, headLen);
    }

    @Test
    void readFile_underCap_returnedInFull() {
        String content = tools.readFile("/notes.txt");
        assertEquals("hello world", content);
        assertFalse(content.contains("[truncated"));
    }

    @Test
    void readFile_traversalEscapeInsideSubpath_isRejected() {
        // "/sub/../../etc/passwd" normalizes above basePath -> rejected.
        assertTrue(tools.readFile("/sub/../../etc/passwd").startsWith("ERROR: path traversal denied"));
    }

    // --- @Tool discoverability (proves the ChatClient wiring without a network) --------

    @Test
    void toolCallbacks_areDiscoverableWithExpectedNames() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        assertNotNull(callbacks);
        Set<String> names = Arrays.stream(callbacks)
                .map(c -> c.getToolDefinition().name())
                .collect(Collectors.toSet());
        // These are the exact names declared via @Tool(name=...); ChatClient registers these.
        assertTrue(names.contains("list_files"), "list_files must be registered: " + names);
        assertTrue(names.contains("read_file"), "read_file must be registered: " + names);
        assertTrue(names.contains("write_file"), "write_file must be registered: " + names);
        assertEquals(3, names.size(), "three file tools should be exposed: " + names);
    }

    @Test
    void toolCallbacks_haveNonEmptyDescriptions() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        for (ToolCallback c : callbacks) {
            assertFalse(c.getToolDefinition().description().isBlank(),
                    "tool " + c.getToolDefinition().name() + " needs a description for the model");
        }
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
    void writeFile_rejectsTraversal() {
        String result = tools.writeFile("../outside.txt", "x");
        assertTrue(result.startsWith("ERROR:"), result);
    }

    @Test
    void toolCallback_listFiles_invocableByNameAndReturnsListing() {
        // Calling the callback directly mirrors what the ToolCallingManager does internally when the
        // model requests the tool — proves the method is callable end-to-end without a real LLM.
        ToolCallback listFiles = Arrays.stream(ToolCallbacks.from(tools))
                .filter(c -> "list_files".equals(c.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String jsonArgs = "{\"path\":\"/\"}";
        String result = listFiles.call(jsonArgs);
        assertNotNull(result);
        assertTrue(result.contains("notes.txt") || result.contains("sub") || result.equals("(empty directory)"),
                result);
    }
}
