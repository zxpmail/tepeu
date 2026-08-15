package com.tepeu.service;

import com.tepeu.model.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 记忆双写的文件侧：DB 为权威源，Markdown 供 Agent 阅读。
 * 关联：MemoryService、WorkspacePathResolver。
 */
@Component
public class MemoryFileMirror {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileMirror.class);
    static final String REL_DIR = ".tepeu/memory";

    private final WorkspacePathResolver pathResolver;

    public MemoryFileMirror(WorkspacePathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    /** 将一条记忆镜像为 workspace 下可读 MD；失败只打日志，不影响 DB 事务语义。 */
    public void write(Memory memory) {
        if (memory == null || memory.getWorkspaceId() == null || memory.getId() == null) {
            return;
        }
        try {
            Path dir = pathResolver.resolveBasePath(memory.getWorkspaceId()).resolve(REL_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(safeFileName(memory.getId()) + ".md");
            Files.writeString(file, toMarkdown(memory), StandardCharsets.UTF_8);
        } catch (RuntimeException | IOException e) {
            log.warn("MemoryFileMirror.write failed id={}: {}", memory.getId(), e.toString());
        }
    }

    /** 删除镜像文件（DB 删除后调用）。 */
    public void delete(String workspaceId, String memoryId) {
        if (workspaceId == null || memoryId == null) return;
        try {
            Path file = pathResolver.resolveBasePath(workspaceId)
                    .resolve(REL_DIR)
                    .resolve(safeFileName(memoryId) + ".md");
            Files.deleteIfExists(file);
        } catch (RuntimeException | IOException e) {
            log.warn("MemoryFileMirror.delete failed id={}: {}", memoryId, e.toString());
        }
    }

    static String toMarkdown(Memory m) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("id: ").append(nullToEmpty(m.getId())).append('\n');
        sb.append("source: ").append(nullToEmpty(m.getSource())).append('\n');
        List<String> tags = m.getTags();
        if (tags != null && !tags.isEmpty()) {
            sb.append("tags: ").append(String.join(", ", tags)).append('\n');
        }
        sb.append("---\n\n");
        sb.append(nullToEmpty(m.getContent()));
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String safeFileName(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
