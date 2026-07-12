package com.tepeu.repository;

import com.tepeu.model.FileVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate-based repository for the {@code file_version} table.
 *
 * <p>Versions are per-file: {@link #save(FileVersion)} auto-increments {@code version_no}
 * within the scope of {@code (workspace_id, file_path)} via
 * {@code SELECT COALESCE(MAX(version_no), 0) + 1}.
 */
@Repository
public class FileVersionRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<FileVersion> mapper;

    public FileVersionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = (rs, rowNum) -> {
            FileVersion fv = new FileVersion();
            fv.setId(rs.getString("id"));
            fv.setWorkspaceId(rs.getString("workspace_id"));
            fv.setFilePath(rs.getString("file_path"));
            fv.setVersionNo(rs.getInt("version_no"));
            fv.setContentRef(rs.getString("content_ref"));
            fv.setCreatedBySession(rs.getString("created_by_session"));
            fv.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            return fv;
        };
    }

    /**
     * Return all versions for a file, newest first.
     */
    public List<FileVersion> findByFilePath(String workspaceId, String filePath) {
        return jdbc.query(
                "SELECT * FROM file_version WHERE workspace_id = ? AND file_path = ? ORDER BY version_no DESC",
                mapper, workspaceId, filePath);
    }

    /**
     * Look up a single version by its primary key.
     */
    public Optional<FileVersion> findById(String id) {
        List<FileVersion> results = jdbc.query(
                "SELECT * FROM file_version WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Insert a new version with an auto-incremented {@code version_no}.
     *
     * <p>The version number is computed as {@code MAX(version_no) + 1} for the same
     * {@code (workspace_id, file_path)}. If {@code id} is null a UUID is generated.
     */
    public FileVersion save(FileVersion fileVersion) {
        if (fileVersion.getId() == null) {
            fileVersion.setId(UUID.randomUUID().toString());
        }
        Integer nextVersion = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) + 1 FROM file_version WHERE workspace_id = ? AND file_path = ?",
                Integer.class, fileVersion.getWorkspaceId(), fileVersion.getFilePath());
        fileVersion.setVersionNo(nextVersion);
        fileVersion.setCreatedAt(LocalDateTime.now());
        jdbc.update(
                "INSERT INTO file_version (id, workspace_id, file_path, version_no, content_ref, created_by_session, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                fileVersion.getId(), fileVersion.getWorkspaceId(), fileVersion.getFilePath(),
                fileVersion.getVersionNo(), fileVersion.getContentRef(),
                fileVersion.getCreatedBySession(), fileVersion.getCreatedAt());
        return fileVersion;
    }

    /**
     * Delete a version record by its primary key.
     */
    public void deleteById(String id) {
        jdbc.update("DELETE FROM file_version WHERE id = ?", id);
    }
}
