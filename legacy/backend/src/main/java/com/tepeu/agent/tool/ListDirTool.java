package com.tepeu.agent.tool;

import com.tepeu.service.WorkspacePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Agent 工具 — 列出工作区内指定目录的文件与子目录。
 * 工具名 {@code list_files}，与 {@link ReadFileTool}、{@link WriteFileTool} 并列。
 */
@Component
public class ListDirTool extends WorkspaceBoundTool {

    @Autowired
    public ListDirTool(WorkspacePathResolver pathResolver) {
        super(pathResolver);
    }

    /** 测试缝：固定 basePath */
    ListDirTool(Path basePath) {
        super(basePath);
    }

    /** 跨包单测工厂 */
    public static ListDirTool forTests(Path basePath) {
        return new ListDirTool(basePath);
    }

    @Tool(name = "list_files", description =
            "List files and subdirectories directly under a path inside the workspace. "
            + "Returns one entry per line as '[DIR] name' or '[FILE] name'. "
            + "Use this to discover what files exist before reading them. "
            + "Parameter 'path' is workspace-relative, e.g. \"/\" for the workspace root.")
    public String listFiles(
            @ToolParam(description = "Workspace-relative path to list; \"/\" means the workspace root.")
            String path) {
        Path target = resolveSafely(path);
        if (target == null) {
            return "ERROR: path traversal denied";
        }
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            return "ERROR: directory not found: " + path;
        }
        try (Stream<Path> entries = Files.list(target)) {
            String listing = entries
                    .sorted(this::compareByName)
                    .map(p -> {
                        String name = p.getFileName() != null ? p.getFileName().toString() : p.toString();
                        return (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + name;
                    })
                    .collect(Collectors.joining("\n"));
            return listing.isEmpty() ? "(empty directory)" : listing;
        } catch (IOException e) {
            return "ERROR: failed to list directory: " + e.getMessage();
        }
    }

    private int compareByName(Path a, Path b) {
        boolean aDir = Files.isDirectory(a);
        boolean bDir = Files.isDirectory(b);
        if (aDir != bDir) {
            return aDir ? -1 : 1;
        }
        String an = a.getFileName() != null ? a.getFileName().toString() : "";
        String bn = b.getFileName() != null ? b.getFileName().toString() : "";
        return an.compareTo(bn);
    }
}
