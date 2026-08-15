package com.tepeu.agent.tool;

import com.tepeu.agent.hook.HallucinationGuard;
import com.tepeu.service.FileVersionService;
import com.tepeu.service.WorkspacePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 工具 — 写入文本内容到工作区内文件。
 * 父目录必须已存在（幻觉门禁）；覆盖已有文件，内容上限 256KB。
 * 写入成功后自动创建文件版本快照。
 */
@Component
public class WriteFileTool extends WorkspaceBoundTool {

    private static final Logger log = LoggerFactory.getLogger(WriteFileTool.class);

    static final int MAX_WRITE_BYTES = 256 * 1024;

    private final HallucinationGuard hallucinationGuard;
    private final FileVersionService fileVersionService;
    private final AtomicReference<String> activeWorkspaceId = new AtomicReference<>();

    @Autowired
    public WriteFileTool(
            WorkspacePathResolver pathResolver,
            HallucinationGuard hallucinationGuard,
            FileVersionService fileVersionService) {
        super(pathResolver);
        this.hallucinationGuard = hallucinationGuard;
        this.fileVersionService = fileVersionService;
    }

    /** 测试缝：固定 basePath，不打版本 */
    WriteFileTool(Path basePath) {
        super(basePath);
        this.hallucinationGuard = new HallucinationGuard();
        this.fileVersionService = null;
    }

    /** 跨包单测工厂 */
    public static WriteFileTool forTests(Path basePath) {
        return new WriteFileTool(basePath);
    }

    @Override
    public void bindWorkspace(String workspaceId) {
        super.bindWorkspace(workspaceId);
        activeWorkspaceId.set(workspaceId);
    }

    @Override
    public void unbindWorkspace() {
        super.unbindWorkspace();
        activeWorkspaceId.set(null);
    }

    @Tool(name = "write_file", description =
            "Write text content to a file inside the workspace. Parent directory must already exist. "
            + "Overwrites existing files. Content is capped at 256KB. "
            + "Parameter 'path' is workspace-relative; 'content' is the full file text.")
    public String writeFile(
            @ToolParam(description = "Workspace-relative path of the file to write.")
            String path,
            @ToolParam(description = "Full text content to write.")
            String content) {
        Path target = resolveSafely(path);
        if (target == null) {
            return "ERROR: path traversal denied";
        }
        String hallu = hallucinationGuard.checkWriteParent(target);
        if (hallu != null) {
            return hallu;
        }
        if (content == null) {
            content = "";
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_BYTES) {
            return "ERROR: content too large (" + bytes.length + " bytes, max " + MAX_WRITE_BYTES + ")";
        }
        try {
            Files.write(target, bytes);
            snapshotVersion(path, content);
            log.info("write_file → {}", target.toAbsolutePath());
            // 结果含内容摘要，便于 UI/验收展示（非仅字节数）
            String preview = content.length() <= 400 ? content : content.substring(0, 400) + "…";
            return "OK: wrote " + bytes.length + " bytes to " + path
                    + "\n--- content preview ---\n" + preview;
        } catch (IOException e) {
            return "ERROR: failed to write file: " + e.getMessage();
        }
    }

    /** 自动版本；无工作区或测试缝时跳过 */
    private void snapshotVersion(String path, String content) {
        if (fileVersionService == null) {
            return;
        }
        String ws = activeWorkspaceId.get();
        if (ws == null || ws.isBlank() || path == null || path.isBlank()) {
            return;
        }
        try {
            fileVersionService.createVersion(ws, path, content, null);
        } catch (RuntimeException e) {
            log.warn("write_file 自动版本失败 path={}: {}", path, e.toString());
        }
    }
}
