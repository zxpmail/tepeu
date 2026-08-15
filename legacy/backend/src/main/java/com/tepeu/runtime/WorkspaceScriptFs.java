package com.tepeu.runtime;

import com.tepeu.service.WorkspacePathResolver;
import org.graalvm.polyglot.HostAccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 注入技能脚本的唯一主机 API：仅允许读写当前 workspace 内路径。
 * 关联：ScriptSandbox、ADR-015。
 */
public final class WorkspaceScriptFs {

    public static final int MAX_BYTES = 256 * 1024;

    private final Path workspaceRoot;

    public WorkspaceScriptFs(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    /** 读文本；越界或非法路径抛错（由脚本侧看到） */
    @HostAccess.Export
    public String readText(String path) throws IOException {
        Path target = resolveOrThrow(path);
        if (!Files.isRegularFile(target)) {
            throw new IOException("file not found: " + path);
        }
        long size = Files.size(target);
        if (size > MAX_BYTES) {
            throw new IOException("file too large (max " + MAX_BYTES + " bytes)");
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    /** 写文本；自动创建父目录 */
    @HostAccess.Export
    public void writeText(String path, String content) throws IOException {
        if (content == null) {
            content = "";
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("content too large (max " + MAX_BYTES + " bytes)");
        }
        Path target = resolveOrThrow(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    @HostAccess.Export
    public boolean exists(String path) {
        Path target = WorkspacePathResolver.resolveSafely(workspaceRoot, path);
        return target != null && Files.exists(target);
    }

    private Path resolveOrThrow(String path) {
        Path target = WorkspacePathResolver.resolveSafely(workspaceRoot, path);
        if (target == null) {
            throw new SecurityException("path outside workspace: " + path);
        }
        return target.toAbsolutePath().normalize();
    }
}
