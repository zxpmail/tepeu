package com.tepeu.service;

import com.tepeu.model.FileVersion;
import com.tepeu.repository.FileVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for file version management.
 *
 * <p>Each version stores a snapshot of file content on disk under
 * {@code <workspaceRoot>/.versions/<uuid>} and a corresponding record in the
 * {@code file_version} table. Restoring a version copies the snapshot content
 * back to the original file path.
 */
@Service
public class FileVersionService {

    private static final Logger log = LoggerFactory.getLogger(FileVersionService.class);
    private static final String VERSIONS_SUBDIR = ".versions";

    private final FileVersionRepository repository;
    private final WorkspaceService workspaceService;

    public FileVersionService(FileVersionRepository repository, WorkspaceService workspaceService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
    }

    /**
     * List all versions for a file, ordered by version_no descending.
     */
    public List<FileVersion> listVersions(String workspaceId, String filePath) {
        return repository.findByFilePath(workspaceId, filePath);
    }

    /**
     * Get a single version record by its ID.
     */
    public Optional<FileVersion> getVersion(String id) {
        return repository.findById(id);
    }

    /**
     * Create a new version snapshot of a file.
     *
     * <ol>
     *   <li>Writes content to {@code <workspaceRoot>/.versions/<uuid>} on disk.</li>
     *   <li>Inserts a {@code file_version} record with {@code content_ref} pointing to the disk path.</li>
     * </ol>
     *
     * @param workspaceId the workspace owning the file
     * @param filePath    workspace-relative file path
     * @param content     the file content to snapshot
     * @param sessionId   optional session identifier that created this version
     * @return the saved {@link FileVersion} record
     * @throws RuntimeException if disk I/O fails
     */
    public FileVersion createVersion(String workspaceId, String filePath, String content, String sessionId) {
        Path workspaceRoot = resolveWorkspaceRoot(workspaceId);
        Path versionsDir = workspaceRoot.resolve(VERSIONS_SUBDIR);
        try {
            Files.createDirectories(versionsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create versions directory: " + versionsDir, e);
        }

        String versionUuid = UUID.randomUUID().toString();
        Path contentPath = versionsDir.resolve(versionUuid);
        try {
            Files.writeString(contentPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write version content to: " + contentPath, e);
        }

        FileVersion fv = new FileVersion();
        fv.setWorkspaceId(workspaceId);
        fv.setFilePath(filePath);
        fv.setContentRef(contentPath.toString());
        fv.setCreatedBySession(sessionId);
        FileVersion saved = repository.save(fv);
        log.debug("Created version {} for file {} in workspace {}", saved.getVersionNo(), filePath, workspaceId);
        return saved;
    }

    /**
     * Restore file content from a historical version.
     *
     * <ol>
     *   <li>Loads the version record by ID.</li>
     *   <li>Reads content from the version's {@code content_ref} disk path.</li>
     *   <li>Writes it back to the original file path (path-traversal guarded).</li>
     * </ol>
     *
     * @param id the version record ID
     * @return the {@link FileVersion} record (content can be re-read from the file)
     * @throws IllegalArgumentException if the version ID is not found
     * @throws SecurityException        if the stored file path would escape the workspace root
     * @throws RuntimeException         if disk I/O fails
     */
    public FileVersion restoreVersion(String id) {
        FileVersion fv = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + id));

        Path contentPath = Paths.get(fv.getContentRef());
        String content;
        try {
            content = Files.readString(contentPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read version content from: " + contentPath, e);
        }

        Path workspaceRoot = resolveWorkspaceRoot(fv.getWorkspaceId());
        Path target = workspaceRoot.resolve("." + fv.getFilePath()).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal detected in version file_path: " + fv.getFilePath());
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to restore file content to: " + target, e);
        }

        log.debug("Restored version {} of file {} in workspace {}", fv.getVersionNo(), fv.getFilePath(), fv.getWorkspaceId());
        return fv;
    }

    /**
     * Read the raw content of a version from its content_ref on disk.
     *
     * @param versionId the version record ID
     * @return the file content as a string
     * @throws IllegalArgumentException if the version ID is not found
     * @throws RuntimeException         if disk I/O fails
     */
    public String readVersionContent(String versionId) {
        FileVersion fv = repository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
        try {
            return Files.readString(Paths.get(fv.getContentRef()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read version content from: " + fv.getContentRef(), e);
        }
    }

    /**
     * Resolve the workspace root path (same logic as {@code FileController.resolveBasePath}).
     */
    private Path resolveWorkspaceRoot(String workspaceId) {
        var workspace = workspaceService.getWorkspace(workspaceId);
        String rootPath = workspace.map(w -> w.getRootPath())
                .orElse("workspaces/" + workspaceId);
        return Paths.get(System.getProperty("user.dir"), rootPath).normalize();
    }
}
