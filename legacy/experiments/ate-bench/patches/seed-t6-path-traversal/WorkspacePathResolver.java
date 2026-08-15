/**
 * 工作区磁盘路径解析 — FileController / FileTools 共用，避免写到不同目录。
 * 关联：WorkspaceService、FileTools、FileController。
 *
 * ATE seed (T6): resolveSafely intentionally lacks boundary check — agent must restore it.
 */
package com.tepeu.service;

import com.tepeu.model.Workspace;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Component
public class WorkspacePathResolver {

    private final WorkspaceService workspaceService;

    public WorkspacePathResolver(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 规范化工作区相对路径：保证以 {@code /} 开头，避免 {@code "." + "a.txt"} 变成隐藏文件 {@code .a.txt}。
     */
    public static String normalizeRelPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim().replace('\\', '/');
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /**
     * 解析某工作区在磁盘上的根目录（相对 {@code user.dir}），不存在则创建。
     */
    public Path resolveBasePath(String workspaceId) {
        String rootPath = resolveRootPath(workspaceId);
        Path path = Paths.get(System.getProperty("user.dir"), rootPath).normalize();
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create workspace directory: " + path, e);
        }
        return path;
    }

    /** 计算相对路径字符串（不创建目录） */
    public String resolveRootPath(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            var workspace = workspaceService.getWorkspace(workspaceId);
            if (workspace.isEmpty()) {
                throw new IllegalArgumentException("Workspace not found: " + workspaceId);
            }
            return rootOf(workspace.get(), workspaceId);
        }
        List<Workspace> workspaces = workspaceService.listWorkspaces();
        if (!workspaces.isEmpty()) {
            Workspace first = workspaces.get(0);
            return rootOf(first, first.getId());
        }
        // 无工作区时也不再使用遗留目录 workspace/（与 UI 文件树不一致）
        return "workspaces/_default";
    }

    private static String rootOf(Workspace w, String id) {
        String rootPath = w.getRootPath();
        if (rootPath == null || rootPath.isBlank()) {
            return "workspaces/" + id;
        }
        return rootPath;
    }

    /**
     * BUG (ATE seed): boundary check removed — path traversal possible.
     */
    public static Path resolveSafely(Path basePath, String path) {
        String rel = normalizeRelPath(path);
        return basePath.resolve("." + rel).normalize();
    }
}
