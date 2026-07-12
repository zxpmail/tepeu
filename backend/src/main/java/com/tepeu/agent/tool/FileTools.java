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
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 智能体文件工具 — 读写当前绑定工作区目录（与 FileController 同一套路径）。
 * 关联：WorkspacePathResolver、AgentOrchestrator、ChatController。
 *
 * <p>不要提供 public 无参构造：Spring 若误用无参构造，会把根目录钉死在
 * {@code user.dir/workspace}，生成的文件左侧文件树看不见。
 */
@Component
public class FileTools {

    private static final Logger log = LoggerFactory.getLogger(FileTools.class);

    static final int MAX_READ_BYTES = 8 * 1024;
    static final int MAX_WRITE_BYTES = 256 * 1024;

    private final WorkspacePathResolver pathResolver;
    /** 测试注入固定根；生产为 null */
    private final Path fixedBasePath;
    /** 当前对话已解析的工作区根（绝对路径；工具回调可能在别的线程） */
    private final AtomicReference<Path> activeBasePath = new AtomicReference<>();

    @Autowired
    public FileTools(WorkspacePathResolver pathResolver) {
        this.pathResolver = pathResolver;
        this.fixedBasePath = null;
    }

    /** 测试缝：固定 basePath，不依赖 WorkspaceService */
    FileTools(Path basePath) {
        this.pathResolver = null;
        this.fixedBasePath = basePath.toAbsolutePath().normalize();
    }

    /** 仅供同包单测；包内可见，避免 Spring 选用无参构造 */
    FileTools() {
        this(Paths.get(System.getProperty("user.dir"), "workspace"));
    }

    /** 跨包单测工厂：固定根目录，不走 Spring */
    public static FileTools forTests(Path basePath) {
        return new FileTools(basePath);
    }

    /** 本轮对话开始时绑定工作区，结束时 {@link #unbindWorkspace()} */
    public void bindWorkspace(String workspaceId) {
        if (fixedBasePath != null) {
            activeBasePath.set(fixedBasePath);
            return;
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            activeBasePath.set(null);
            log.warn("FileTools.bindWorkspace: workspaceId 为空");
            return;
        }
        Path base = pathResolver.resolveBasePath(workspaceId);
        activeBasePath.set(base);
        log.debug("FileTools 已绑定 workspaceId={} → {}", workspaceId, base);
    }

    public void unbindWorkspace() {
        activeBasePath.set(null);
    }

    Path currentBasePath() {
        if (fixedBasePath != null) {
            return fixedBasePath;
        }
        Path bound = activeBasePath.get();
        if (bound != null) {
            return bound;
        }
        // 未绑定：与 FileController 一致，用第一个工作区（不再静默落到遗留 workspace/）
        log.warn("FileTools 在未 bindWorkspace 时被调用，回退到默认工作区根目录");
        return pathResolver.resolveBasePath(null);
    }

    @Tool(name = "list_files", description =
            "List files and subdirectories directly under a path inside the workspace. " +
            "Returns one entry per line as '[DIR] name' or '[FILE] name'. " +
            "Use this to discover what files exist before reading them. " +
            "Parameter 'path' is workspace-relative, e.g. \"/\" for the workspace root.")
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
                    .sorted(FileTools::compareByName)
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

    @Tool(name = "read_file", description =
            "Read the text content of a file inside the workspace. " +
            "Content is capped at 8KB to fit the model context; larger files are truncated. " +
            "Parameter 'path' is workspace-relative.")
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
            if (size > MAX_READ_BYTES) {
                String head = Files.readString(target).substring(0, MAX_READ_BYTES);
                return head + "\n...[truncated: file is " + size + " bytes, showing first "
                        + MAX_READ_BYTES + " bytes]";
            }
            return Files.readString(target);
        } catch (IOException e) {
            return "ERROR: failed to read file: " + e.getMessage();
        } catch (OutOfMemoryError e) {
            return "ERROR: file too large to read into memory";
        }
    }

    @Tool(name = "write_file", description =
            "Write text content to a file inside the workspace. Creates parent directories if needed. " +
            "Overwrites existing files. Content is capped at 256KB. " +
            "Parameter 'path' is workspace-relative; 'content' is the full file text.")
    public String writeFile(
            @ToolParam(description = "Workspace-relative path of the file to write.")
            String path,
            @ToolParam(description = "Full text content to write.")
            String content) {
        Path target = resolveSafely(path);
        if (target == null) {
            return "ERROR: path traversal denied";
        }
        if (content == null) {
            content = "";
        }
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_BYTES) {
            return "ERROR: content too large (" + bytes.length + " bytes, max " + MAX_WRITE_BYTES + ")";
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, bytes);
            log.info("write_file → {}", target.toAbsolutePath());
            return "OK: wrote " + bytes.length + " bytes to " + path;
        } catch (IOException e) {
            return "ERROR: failed to write file: " + e.getMessage();
        }
    }

    Path resolveSafely(String path) {
        return WorkspacePathResolver.resolveSafely(currentBasePath(), path);
    }

    private static int compareByName(Path a, Path b) {
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
