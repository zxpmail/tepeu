package com.tepeu.repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.tepeu.model.Memory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MemoryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<Memory> mapper;

    public MemoryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, rowNum) -> {
            Memory m = new Memory();
            m.setId(rs.getString("id"));
            m.setWorkspaceId(rs.getString("workspace_id"));
            m.setSource(rs.getString("source"));
            m.setContent(rs.getString("content"));
            try {
                String tagsStr = rs.getString("tags");
                if (tagsStr != null && !tagsStr.isEmpty()) {
                    m.setTags(objectMapper.readValue(tagsStr, new TypeReference<List<String>>() {}));
                }
            } catch (Exception e) {
                m.setTags(List.of());
            }
            m.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            m.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
            return m;
        };
    }

    public List<Memory> findByWorkspaceId(String workspaceId, int limit, String cursor) {
        if (cursor != null && !cursor.isEmpty()) {
            return jdbc.query(
                    "SELECT * FROM memory WHERE workspace_id = ? AND created_at < ? ORDER BY created_at DESC LIMIT ?",
                    mapper, workspaceId, cursor, limit);
        }
        return jdbc.query(
                "SELECT * FROM memory WHERE workspace_id = ? ORDER BY created_at DESC LIMIT ?",
                mapper, workspaceId, limit);
    }

    public List<Memory> search(String workspaceId, String query, List<String> tags, int limit, String cursor) {
        // SQLite FTS-like approach: use LIKE for simple text search
        StringBuilder sql = new StringBuilder("SELECT * FROM memory WHERE workspace_id = ?");
        List<Object> paramList = new ArrayList<>();
        paramList.add(workspaceId);

        boolean hasQuery = query != null && !query.isEmpty();
        if (hasQuery) {
            sql.append(" AND content LIKE ?");
            paramList.add("%" + query + "%");
        }

        boolean hasTags = tags != null && !tags.isEmpty();
        if (hasTags) {
            sql.append(" AND (");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("tags LIKE ?");
                // Match against JSON representation: wrap each tag in quotes
                paramList.add("%\"" + tags.get(i) + "\"%");
            }
            sql.append(")");
        }

        if (cursor != null && !cursor.isEmpty()) {
            sql.append(" AND created_at < ?");
            paramList.add(cursor);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        paramList.add(limit);

        return jdbc.query(sql.toString(), mapper, paramList.toArray());
    }

    public Optional<Memory> findById(String id) {
        List<Memory> results = jdbc.query("SELECT * FROM memory WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Memory save(Memory memory) {
        if (memory.getId() == null) {
            memory.setId(UUID.randomUUID().toString());
        }
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        try {
            String tagsJson = memory.getTags() != null
                    ? objectMapper.writeValueAsString(memory.getTags())
                    : "[]";
            jdbc.update("INSERT INTO memory (id, workspace_id, source, content, tags, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    memory.getId(), memory.getWorkspaceId(), memory.getSource(),
                    memory.getContent(), tagsJson, memory.getCreatedAt(), memory.getUpdatedAt());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save memory", e);
        }
        return memory;
    }

    public Memory update(Memory memory) {
        memory.setUpdatedAt(LocalDateTime.now());
        try {
            String tagsJson = memory.getTags() != null
                    ? objectMapper.writeValueAsString(memory.getTags())
                    : "[]";
            jdbc.update("UPDATE memory SET content = ?, tags = ?, updated_at = ? WHERE id = ?",
                    memory.getContent(), tagsJson, memory.getUpdatedAt(), memory.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update memory", e);
        }
        return memory;
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM memory WHERE id = ?", id);
    }
}
