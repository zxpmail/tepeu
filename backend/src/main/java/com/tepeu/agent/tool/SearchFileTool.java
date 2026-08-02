package com.tepeu.agent.tool;

import com.tepeu.service.WorkspacePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Agent 工具 — 在工作区内按文件名/内容关键字搜索。
 * 工具名 {@code search_files}，只读，结果条数有上限。
 */
@Component
public class SearchFileTool extends WorkspaceBoundTool {

    /** 默认最多返回条数 */
    static final int DEFAULT_MAX_RESULTS = 20;
    /** 硬上限 */
    static final int HARD_MAX_RESULTS = 50;
    /** 参与内容搜索的单文件上限（字节） */
    static final int MAX_CONTENT_BYTES = 256 * 1024;
    /** 跳过常见依赖/构建目录 */
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "dist", "build", ".idea", ".gradle", "__pycache__");

    @Autowired
    public SearchFileTool(WorkspacePathResolver pathResolver) {
        super(pathResolver);
    }

    /** 测试缝：固定 basePath */
    SearchFileTool(Path basePath) {
        super(basePath);
    }

    /** 跨包单测工厂 */
    public static SearchFileTool forTests(Path basePath) {
        return new SearchFileTool(basePath);
    }

    @Tool(name = "search_files", description =
            "Search the workspace for files whose name or text content contains a query string. "
            + "Returns matching paths (and a short content snippet when the match is in file body). "
            + "Use this to find code or filenames without listing every directory. "
            + "Parameter 'path' is the workspace-relative start directory (\"/\" = root).")
    public String searchFiles(
            @ToolParam(description = "Case-insensitive substring to match in file names or text content.")
            String query,
            @ToolParam(description = "Workspace-relative start directory; \"/\" means workspace root.")
            String path,
            @ToolParam(description = "Optional max results (default 20, max 50). Omit to use 20.")
            Integer maxResults) {
        if (query == null || query.isBlank()) {
            return "ERROR: empty query";
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        int requested = maxResults == null ? DEFAULT_MAX_RESULTS : maxResults;
        if (requested < 1) requested = 1;
        if (requested > HARD_MAX_RESULTS) requested = HARD_MAX_RESULTS;
        final int limit = requested;

        Path start = resolveSafely(path == null || path.isBlank() ? "/" : path);
        if (start == null) {
            return "ERROR: path traversal denied";
        }
        if (!Files.exists(start) || !Files.isDirectory(start)) {
            return "ERROR: directory not found: " + path;
        }

        Path base = currentBasePath();
        List<String> hits = new ArrayList<>();
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (!dir.equals(start) && SKIP_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (hits.size() >= limit) {
                        return FileVisitResult.TERMINATE;
                    }
                    String rel = relativize(base, file);
                    String name = file.getFileName() != null ? file.getFileName().toString() : "";
                    if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                        hits.add("[NAME] " + rel);
                        return FileVisitResult.CONTINUE;
                    }
                    String contentHit = matchContent(file, needle);
                    if (contentHit != null) {
                        hits.add("[CONTENT] " + rel + " — " + contentHit);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "ERROR: failed to search: " + e.getMessage();
        }

        if (hits.isEmpty()) {
            return "(no matches)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("found=").append(hits.size());
        if (hits.size() >= limit) {
            sb.append(" (capped at ").append(limit).append(')');
        }
        sb.append('\n');
        for (String h : hits) {
            sb.append(h).append('\n');
        }
        return sb.toString().trim();
    }

    private static String relativize(Path base, Path file) {
        String rel = base.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        return rel.isEmpty() ? file.getFileName().toString() : rel;
    }

    /** 内容命中时返回一行摘要；跳过过大/明显二进制 */
    private static String matchContent(Path file, String needleLower) {
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_CONTENT_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            if (looksBinary(bytes)) {
                return null;
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            String lower = text.toLowerCase(Locale.ROOT);
            int idx = lower.indexOf(needleLower);
            if (idx < 0) {
                return null;
            }
            int lineStart = text.lastIndexOf('\n', idx) + 1;
            int lineEnd = text.indexOf('\n', idx);
            if (lineEnd < 0) lineEnd = text.length();
            String line = text.substring(lineStart, lineEnd).trim();
            if (line.length() > 120) {
                line = line.substring(0, 120) + "…";
            }
            return line;
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    private static boolean looksBinary(byte[] bytes) {
        int n = Math.min(bytes.length, 512);
        for (int i = 0; i < n; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }
}
