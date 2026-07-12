package com.tepeu.repository;

import com.tepeu.model.Skill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * skill 表 JdbcTemplate 访问。
 * 关联：SkillService、DatabaseConfig。
 */
@Repository
public class SkillRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Skill> mapper = (rs, rowNum) -> {
        Skill s = new Skill();
        s.setId(rs.getString("id"));
        s.setWorkspaceId(rs.getString("workspace_id"));
        s.setSlug(rs.getString("slug"));
        s.setName(rs.getString("name"));
        s.setDescription(rs.getString("description"));
        s.setContent(rs.getString("content"));
        s.setEnabled(rs.getInt("enabled") != 0);
        s.setBuiltin(rs.getInt("builtin") != 0);
        s.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        s.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return s;
    };

    public SkillRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Skill> findByWorkspaceId(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM skill WHERE workspace_id = ? ORDER BY builtin DESC, name ASC",
                mapper, workspaceId);
    }

    public List<Skill> findEnabledByWorkspaceId(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM skill WHERE workspace_id = ? AND enabled = 1 ORDER BY name ASC",
                mapper, workspaceId);
    }

    public Optional<Skill> findById(String id) {
        List<Skill> rows = jdbc.query("SELECT * FROM skill WHERE id = ?", mapper, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<Skill> findByWorkspaceAndSlug(String workspaceId, String slug) {
        List<Skill> rows = jdbc.query(
                "SELECT * FROM skill WHERE workspace_id = ? AND slug = ?",
                mapper, workspaceId, slug);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Skill save(Skill skill) {
        if (skill.getId() == null) {
            skill.setId(UUID.randomUUID().toString());
        }
        LocalDateTime now = LocalDateTime.now();
        if (skill.getCreatedAt() == null) {
            skill.setCreatedAt(now);
        }
        skill.setUpdatedAt(now);
        jdbc.update(
                """
                INSERT INTO skill (id, workspace_id, slug, name, description, content, enabled, builtin, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  name = excluded.name,
                  description = excluded.description,
                  content = excluded.content,
                  enabled = excluded.enabled,
                  updated_at = excluded.updated_at
                """,
                skill.getId(),
                skill.getWorkspaceId(),
                skill.getSlug(),
                skill.getName(),
                skill.getDescription(),
                skill.getContent(),
                skill.isEnabled() ? 1 : 0,
                skill.isBuiltin() ? 1 : 0,
                skill.getCreatedAt(),
                skill.getUpdatedAt());
        return skill;
    }

    public void updateEnabled(String id, boolean enabled) {
        jdbc.update(
                "UPDATE skill SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, LocalDateTime.now(), id);
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM skill WHERE id = ?", id) > 0;
    }
}
