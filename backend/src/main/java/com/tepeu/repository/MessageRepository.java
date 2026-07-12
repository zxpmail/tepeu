package com.tepeu.repository;

import com.tepeu.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate repository for {@link Message}. Follows the same pattern as
 * {@link MemoryRepository}: {@link RowMapper} assigned in the constructor, UUID PK generated on
 * insert. Messages are returned in chronological order (oldest first) so the orchestrator can feed
 * them directly into a {@code Prompt} as conversation history.
 */
@Repository
public class MessageRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Message> mapper;

    public MessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = (rs, rowNum) -> {
            Message m = new Message();
            m.setId(rs.getString("id"));
            m.setSessionId(rs.getString("session_id"));
            m.setRole(rs.getString("role"));
            m.setContent(rs.getString("content"));
            m.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            return m;
        };
    }

    /** All messages for a session, oldest first (so they read as conversation history). */
    public List<Message> findBySessionId(String sessionId) {
        return jdbc.query(
                "SELECT * FROM message WHERE session_id = ? ORDER BY created_at ASC, id ASC",
                mapper, sessionId);
    }

    /** 按主键查单条消息。 */
    public Optional<Message> findById(String id) {
        List<Message> results = jdbc.query("SELECT * FROM message WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** 统计会话下消息条数。 */
    public int countBySessionId(String sessionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE session_id = ?", Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    public Message save(Message message) {
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        jdbc.update("INSERT INTO message (id, session_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
                message.getId(), message.getSessionId(), message.getRole(),
                message.getContent(), message.getCreatedAt());
        return message;
    }

    /**
     * Delete all messages for a session. The {@code message → session} FK has
     * {@code ON DELETE CASCADE}, so this is redundant when the session row is removed; kept for
     * explicit/truncation use (e.g. clearing history without deleting the session).
     */
    public void deleteBySessionId(String sessionId) {
        jdbc.update("DELETE FROM message WHERE session_id = ?", sessionId);
    }
}
