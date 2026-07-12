package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.model.FileVersion;
import com.tepeu.service.FileVersionService;
import com.tepeu.service.WorkspacePathResolver;
import com.tepeu.service.WorkspaceService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * File operations controller.
 * Phase 1: list, read, write (basic). Preview and version history added in Phase 4.
 *
 * <p>File isolation per workspace (Spec §3.4): each workspace has its own {@code root_path}.
 * Endpoints accept an optional {@code workspaceId} parameter; when omitted, the first available
 * workspace is used. If no workspaces exist, a default {@code workspace/} directory is used as
 * fallback for backward compatibility.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final WorkspaceService workspaceService;
    private final FileVersionService fileVersionService;
    private final WorkspacePathResolver pathResolver;

    public FileController(WorkspaceService workspaceService,
                          FileVersionService fileVersionService,
                          WorkspacePathResolver pathResolver) {
        this.workspaceService = workspaceService;
        this.fileVersionService = fileVersionService;
        this.pathResolver = pathResolver;
    }

    /**
     * Resolve the base path for the given workspaceId.
     * <ul>
     *   <li>If workspaceId is provided and non-blank, looks up the workspace by ID.
     *   <li>If workspaceId is null/blank, uses the first available workspace's rootPath.
     *   <li>If no workspaces exist, falls back to the default {@code workspace/} directory.
     * </ul>
     *
     * @throws IllegalArgumentException if workspaceId is specified but not found, or its rootPath is missing
     */
    private Path resolveBasePath(String workspaceId) {
        return pathResolver.resolveBasePath(workspaceId);
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<?>> listFiles(
            @RequestParam(required = false, defaultValue = "/") String path,
            @RequestParam(required = false) String workspaceId) {
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, path);
            if (target == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            if (!Files.exists(target) || !Files.isDirectory(target)) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Directory not found"));
            }

            List<Map<String, Object>> items;
            try (var stream = Files.list(target)) {
                items = stream.map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", p.getFileName().toString());
                    item.put("isDirectory", Files.isDirectory(p));
                    item.put("size", -1L);
                    try {
                        item.put("size", Files.size(p));
                        item.put("lastModified", Files.getLastModifiedTime(p).toMillis());
                    } catch (IOException e) {
                        log.warn("Failed to read file metadata: {}", p, e);
                    }
                    return item;
                }).collect(Collectors.toList());
            }
            return ResponseEntity.ok(ApiResponse.success(Map.of("path", path, "items", items)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to list files", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to list files"));
        }
    }

    @PostMapping("/read")
    public ResponseEntity<ApiResponse<?>> readFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("path");
        if (filePath == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "path is required"));
        }
        String workspaceId = body.get("workspaceId");
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, filePath);
            if (target == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            if (!Files.exists(target) || Files.isDirectory(target)) {
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "File not found"));
            }
            String content = Files.readString(target);
            String mimeType = Files.probeContentType(target);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "path", filePath, "content", content, "mimeType", mimeType != null ? mimeType : "text/plain"
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to read file", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to read file"));
        }
    }

    @PostMapping("/write")
    public ResponseEntity<ApiResponse<?>> writeFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("path");
        String content = body.get("content");
        if (filePath == null || content == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "path and content are required"));
        }
        String workspaceId = body.get("workspaceId");
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, filePath);
            if (target == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return ResponseEntity.ok(ApiResponse.success("File written", Map.of("path", filePath)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to write file", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to write file"));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<?>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "/") String path,
            @RequestParam(required = false) String workspaceId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "File is empty"));
        }
        String filename = file.getOriginalFilename();
        if (!isSafeFilename(filename)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("FORBIDDEN", "Invalid filename"));
        }
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, path);
            if (target == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            Files.createDirectories(target);
            Path filePath = target.resolve(filename).normalize();
            if (!filePath.startsWith(basePath)) {       // C1: re-check after resolving filename
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            file.transferTo(filePath.toFile());
            return ResponseEntity.ok(ApiResponse.success("File uploaded", Map.of(
                    "path", path + "/" + filename,
                    "size", file.getSize()
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to upload file"));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestBody Map<String, String> body) {
        String filePath = body.get("path");
        if (filePath == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "path is required"));
        }
        String workspaceId = body.get("workspaceId");
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, filePath);
            if (target == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("FORBIDDEN", "Path traversal denied"));
            }
            Files.deleteIfExists(target);
            return ResponseEntity.ok(ApiResponse.success("File deleted", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete file", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to delete file"));
        }
    }

    /**
     * 以原始字节流返回文件，供浏览器 iframe / img / object 直接预览（HTML、PDF、图片等）。
     * 与 POST /read（JSON 文本）不同，此接口支持二进制且带正确 Content-Type。
     */
    @GetMapping("/raw")
    public void rawFile(
            @RequestParam String path,
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String download,
            HttpServletResponse response) throws IOException {
        if (path == null || path.isBlank()) {
            response.sendError(400, "path is required");
            return;
        }
        try {
            Path basePath = resolveBasePath(workspaceId);
            Path target = WorkspacePathResolver.resolveSafely(basePath, path);
            if (target == null) {
                response.sendError(403, "Path traversal denied");
                return;
            }
            if (!Files.exists(target) || Files.isDirectory(target)) {
                response.sendError(404, "File not found: " + path);
                return;
            }
            String mime = probeMime(target);
            boolean asDownload = "1".equals(download) || "true".equalsIgnoreCase(download);
            response.setContentType(mime);
            String filename = target.getFileName().toString().replace("\"", "");
            String disposition = (asDownload ? "attachment" : "inline") + "; filename=\"" + filename + "\"";
            response.setHeader("Content-Disposition", disposition);
            response.setHeader("Cache-Control", "no-cache");
            Files.copy(target, response.getOutputStream());
            response.flushBuffer();
        } catch (IllegalArgumentException e) {
            response.sendError(404, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to serve raw file {}", path, e);
            response.sendError(500, "Failed to read file");
        }
    }

    /** 推断 MIME：先 probe，再按扩展名兜底（Windows 上 probe 常失败） */
    private static String probeMime(Path target) throws IOException {
        String probed = Files.probeContentType(target);
        if (probed != null && !probed.isBlank()) {
            return probed;
        }
        String name = target.getFileName() != null ? target.getFileName().toString().toLowerCase() : "";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".md") || name.endsWith(".markdown")) return "text/markdown; charset=UTF-8";
        if (name.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (name.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".css")) return "text/css; charset=UTF-8";
        if (name.endsWith(".js")) return "text/javascript; charset=UTF-8";
        return "application/octet-stream";
    }

    // ---------------------------------------------------------------------------
    // Version history & preview (Phase 4)
    // ---------------------------------------------------------------------------

    /**
     * Preview a file version's content directly in the browser (GET, no body).
     *
     * <p>Two modes:
     * <ul>
     *   <li>With {@code id} path variable: loads content from that version's snapshot on disk.</li>
     *   <li>With {@code path} query param: loads the current file content.</li>
     * </ul>
     *
     * <p>Writes raw content to the response with an inferred MIME type, unlike the
     * POST {@code /read} endpoint which returns JSON. This is designed for browser links
     * (e.g. {@code <a href="/api/files/preview/...">}).
     */
    @GetMapping("/preview/{id}")
    public void previewVersion(@PathVariable String id,
                               @RequestParam(required = false) String path,
                               HttpServletResponse response) throws IOException {
        // If the id is a known version ID, serve that snapshot
        var versionOpt = fileVersionService.getVersion(id);
        if (versionOpt.isPresent()) {
            FileVersion fv = versionOpt.get();
            String content = fileVersionService.readVersionContent(id);
            String mime = Files.probeContentType(Paths.get(fv.getFilePath()));
            response.setContentType(mime != null ? mime : "text/plain; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(content);
            return;
        }

        // Otherwise, treat id as a file path for current-content preview
        // (backward compat: caller passes path in the path variable rather than query param)
        String effectivePath = path != null ? path : id;
        try {
            Path basePath = resolveBasePath(null);
            Path target = WorkspacePathResolver.resolveSafely(basePath, effectivePath);
            if (target == null) {
                response.sendError(403, "Path traversal denied");
                return;
            }
            if (!Files.exists(target) || Files.isDirectory(target)) {
                response.sendError(404, "File not found: " + effectivePath);
                return;
            }
            String content = Files.readString(target);
            String mime = Files.probeContentType(target);
            response.setContentType(mime != null ? mime : "text/plain; charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(content);
        } catch (IllegalArgumentException e) {
            response.sendError(404, e.getMessage());
        }
    }

    /**
     * List version history for a file.
     *
     * <p>Returns metadata-only (no content body). The caller can retrieve specific version
     * content via the {@code /preview/{id}} endpoint.
     *
     * @param path        workspace-relative file path (e.g. {@code /notes.txt})
     * @param workspaceId optional; defaults to the first available workspace
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<?>> listVersionHistory(
            @RequestParam String path,
            @RequestParam(required = false) String workspaceId) {
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "path is required"));
        }
        String effectiveWs = resolveWorkspaceId(workspaceId);
        if (effectiveWs == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "No workspace available"));
        }
        List<FileVersion> versions = fileVersionService.listVersions(effectiveWs, path);
        return ResponseEntity.ok(ApiResponse.success(Map.of("path", path, "versions", versions)));
    }

    /**
     * Restore a file from a historical version.
     *
     * <p>The version's snapshot content is written back to the original file path.
     * The restored version metadata is returned without the content body.
     *
     * @param id the version record ID to restore from
     */
    @PostMapping("/restore/{id}")
    public ResponseEntity<ApiResponse<?>> restoreVersion(@PathVariable String id) {
        try {
            FileVersion restored = fileVersionService.restoreVersion(id);
            return ResponseEntity.ok(ApiResponse.success("Version restored", Map.of(
                    "id", restored.getId(),
                    "workspaceId", restored.getWorkspaceId(),
                    "filePath", restored.getFilePath(),
                    "versionNo", restored.getVersionNo(),
                    "createdAt", restored.getCreatedAt().toString()
            )));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("FORBIDDEN", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to restore version: {}", id, e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to restore version"));
        }
    }

    /**
     * Create a version snapshot of a file.
     *
     * <p>The frontend calls this before editing a file (auto-versioning on write).
     *
     * <p>Request body: {@code {workspaceId, path, content, sessionId}}
     */
    @PostMapping("/version")
    public ResponseEntity<ApiResponse<?>> createVersion(@RequestBody Map<String, String> body) {
        String workspaceId = body.get("workspaceId");
        String filePath = body.get("path");
        String content = body.get("content");
        if (workspaceId == null || filePath == null || content == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId, path, and content are required"));
        }
        String sessionId = body.get("sessionId");
        try {
            FileVersion fv = fileVersionService.createVersion(workspaceId, filePath, content, sessionId);
            return ResponseEntity.ok(ApiResponse.success("Version created", Map.of(
                    "id", fv.getId(),
                    "workspaceId", fv.getWorkspaceId(),
                    "filePath", fv.getFilePath(),
                    "versionNo", fv.getVersionNo(),
                    "createdAt", fv.getCreatedAt().toString()
            )));
        } catch (Exception e) {
            log.error("Failed to create version", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to create version"));
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Resolve a workspace ID: if provided and non-blank use it directly; otherwise
     * return the first available workspace's ID; return {@code null} if no workspaces
     * exist and no fallback was possible.
     */
    private String resolveWorkspaceId(String workspaceId) {
        if (workspaceId != null && !workspaceId.isBlank()) {
            return workspaceId;
        }
        var workspaces = workspaceService.listWorkspaces();
        if (!workspaces.isEmpty()) {
            return workspaces.get(0).getId();
        }
        return null;
    }

    /** Reject filenames that could escape basePath or carry control characters. */
    private static boolean isSafeFilename(String name) {
        if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")) {
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        return name.chars().allMatch(c -> c >= 0x20);
    }
}
