package com.tepeu.agent.tool;

import com.tepeu.service.WorkspacePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 工具 — 读取工作区内文件文本内容。
 * 工具名 {@code read_file}，内容上限 8KB，超出截断并标注。
 */
@Component
public class ReadFileTool extends WorkspaceBoundTool {

    static final int MAX_READ_BYTES = 8 * 1024;

    @Autowired
    public ReadFileTool(WorkspacePathResolver pathResolver) {
        super(pathResolver);
    }

    /** 测试缝：固定 basePath */
    ReadFileTool(Path basePath) {
        super(basePath);
    }

    /** 跨包单测工厂 */
    public static ReadFileTool forTests(Path basePath) {
        return new ReadFileTool(basePath);
    }

    @Tool(name = "read_file", description =
            "Read the text content of a file inside the workspace. "
            + "Content is capped at 8KB to fit the model context; larger files are truncated. "
            + "Parameter 'path' is workspace-relative.")
    public String readFile(
            @ToolParam(description = "Workspace-relative path of the file to read.")
            String path) {
        Path target = resolveSafely(path);
        if (target == null) {
            return "ERROR: path traversal denied";
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return "ERROR: file not found: " + path;
        }
        try {
            long size = Files.size(target);
            // 只读前 MAX+1 字节：上限内返回全文，超限返回截断预览。
            // 避免旧实现的整文件 readString 后按字符下标截断——多字节文件会越界崩溃，且大文件整读浪费内存。
            try (InputStream in = Files.newInputStream(target)) {
                byte[] buf = in.readNBytes(MAX_READ_BYTES + 1);
                boolean truncated = buf.length > MAX_READ_BYTES;
                int len = truncated ? MAX_READ_BYTES : buf.length;
                String head = new String(buf, 0, len, StandardCharsets.UTF_8);
                if (truncated) {
                    return head + "\n...[truncated: file is " + size + " bytes, showing first "
                            + MAX_READ_BYTES + " bytes]";
                }
                return head;
            }
        } catch (IOException e) {
            return "ERROR: failed to read file: " + e.getMessage();
        } catch (OutOfMemoryError e) {
            return "ERROR: file too large to read into memory";
        }
    }
}
