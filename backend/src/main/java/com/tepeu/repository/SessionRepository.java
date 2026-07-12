package com.tepeu.repository;

import com.tepeu.model.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link Session}. The {@code session} table is created by
 * {@link com.tepeu.config.DatabaseConfig}; {@code workspace_id} has {@code ON DELETE CASCADE}.
 */
@Repository
public class SessionRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Session> mapper;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = (rs, rowNum) -> {
            Session s = new Session();
            s.setId(rs.getString("id"));
            s.setWorkspaceId(rs.getString("workspace_id"));
            s.setTitle(rs.getString("title"));
            s.setParentSessionId(getStringOrNull(rs, "parent_session_id"));
            s.setForkFromMessageId(getStringOrNull(rs, "fork_from_message_id"));
            s.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            s.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
            return s;
        };
    }

    /** 读取可选列；列不存在时返回 null（迁移前的旧库）。 */
    private static String getStringOrNull(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }

    /** Sessions for a workspace, most-recently-updated first. */
    public List<Session> findByWorkspaceId(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM session WHERE workspace_id = ? ORDER BY updated_at DESC, created_at DESC",
                mapper, workspaceId);
    }

    public Optional<Session> findById(String id) {
        List<Session> results = jdbc.query("SELECT * FROM session WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Session save(Session session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID().toString());
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(now);
        }
        session.setUpdatedAt(now);
        jdbc.update(
                "INSERT INTO session (id, workspace_id, title, created_at, updated_at, parent_session_id, fork_from_message_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                session.getId(), session.getWorkspaceId(), session.getTitle(),
                session.getCreatedAt(), session.getUpdatedAt(),
                session.getParentSessionId(), session.getForkFromMessageId());
        return session;
    }

    /** 仅刷新 updated_at，不改其它字段。 */
    public void touchUpdatedAt(String id) {
        jdbc.update("UPDATE session SET updated_at = ? WHERE id = ?", LocalDateTime.now(), id);
    }

    /** 更新会话标题。 */
    public Optional<Session> updateTitle(String id, String title) {
        int n = jdbc.update(
                "UPDATE session SET title = ?, updated_at = ? WHERE id = ?",
                title, LocalDateTime.now(), id);
        if (n == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM session WHERE id = ?", id);
    }
}
