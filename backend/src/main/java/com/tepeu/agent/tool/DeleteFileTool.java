package com.tepeu.agent.tool;

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

/**
 * Agent 工具 — 删除工作区内文件（需 Hook 审批，kind=file_delete）。
 * 关联：Tools、DangerousToolHook、ToolEventEmittingCallback。
 */
@Component
public class DeleteFileTool extends WorkspaceBoundTool {

    private static final Logger log = LoggerFactory.getLogger(DeleteFileTool.class);

    @Autowired
    public DeleteFileTool(WorkspacePathResolver pathResolver) {
        super(pathResolver);
    }

    /** 测试缝：固定 basePath */
    DeleteFileTool(Path basePath) {
        super(basePath);
    }

    public static DeleteFileTool forTests(Path basePath) {
        return new DeleteFileTool(basePath);
    }

    @Tool(name = "delete_file", description =
            "Delete a file inside the workspace. Directories are not deleted. "
            + "Requires user approval. Parameter 'path' is workspace-relative.")
    public String deleteFile(
            @ToolParam(description = "Workspace-relative path of the file to delete.")
            String path) {
        Path target = resolveSafely(path);
        if (target == null) {
            return "ERROR: path traversal denied";
        }
        if (!Files.exists(target)) {
            return "ERROR: file not found: " + path;
        }
        if (Files.isDirectory(target)) {
            return "ERROR: path is a directory; delete_file only removes files";
        }
        try {
            Files.delete(target);
            log.info("delete_file → {}", target.toAbsolutePath());
            return "OK: deleted " + path;
        } catch (IOException e) {
            return "ERROR: failed to delete file: " + e.getMessage();
        }
    }
}
