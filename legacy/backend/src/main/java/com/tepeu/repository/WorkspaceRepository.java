package com.tepeu.repository;

import com.tepeu.model.Workspace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkspaceRepository {

    private final JdbcTemplate jdbc;

    public WorkspaceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Workspace> mapper = (rs, rowNum) -> {
        Workspace w = new Workspace();
        w.setId(rs.getString("id"));
        w.setName(rs.getString("name"));
        w.setDescription(rs.getString("description"));
        w.setType(rs.getString("type"));
        w.setOwnerId(rs.getString("owner_id"));
        w.setRootPath(rs.getString("root_path"));
        w.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        w.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return w;
    };

    public List<Workspace> findAll() {
        return jdbc.query("SELECT * FROM workspace ORDER BY updated_at DESC", mapper);
    }

    public Optional<Workspace> findById(String id) {
        List<Workspace> results = jdbc.query("SELECT * FROM workspace WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Workspace save(Workspace workspace) {
        if (workspace.getId() == null) {
            workspace.setId(UUID.randomUUID().toString());
        }
        workspace.setCreatedAt(LocalDateTime.now());
        workspace.setUpdatedAt(LocalDateTime.now());
        jdbc.update("INSERT INTO workspace (id, name, description, type, owner_id, root_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                workspace.getId(), workspace.getName(), workspace.getDescription(),
                workspace.getType(), workspace.getOwnerId(), workspace.getRootPath(),
                workspace.getCreatedAt(), workspace.getUpdatedAt());
        return workspace;
    }

    public Workspace update(Workspace workspace) {
        workspace.setUpdatedAt(LocalDateTime.now());
        jdbc.update("UPDATE workspace SET name = ?, description = ?, type = ?, updated_at = ? WHERE id = ?",
                workspace.getName(), workspace.getDescription(),
                workspace.getType(), workspace.getUpdatedAt(), workspace.getId());
        return workspace;
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM workspace WHERE id = ?", id);
    }
}
