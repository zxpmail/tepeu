package com.tepeu.repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.tepeu.model.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 记忆持久化；检索优先 FTS5，失败回退 LIKE。
 * 关联：MemoryService、DatabaseConfig（memory_fts）。
 */
@Repository
public class MemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(MemoryRepository.class);

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
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            try {
                return searchFts(workspaceId, query.trim(), tags, limit, cursor);
            } catch (RuntimeException e) {
                log.debug("FTS search fallback to LIKE: {}", e.getMessage());
            }
        }
        return searchLike(workspaceId, query, tags, limit, cursor);
    }

    /** FTS5 MATCH；按词前缀匹配 */
    private List<Memory> searchFts(
            String workspaceId, String query, List<String> tags, int limit, String cursor) {
        String match = toFtsMatch(query);
        StringBuilder sql = new StringBuilder(
                "SELECT m.* FROM memory m "
                        + "INNER JOIN memory_fts f ON m.id = f.id "
                        + "WHERE f.workspace_id = ? AND memory_fts MATCH ?");
        List<Object> params = new ArrayList<>();
        params.add(workspaceId);
        params.add(match);
        appendTags(sql, params, tags, true);
        if (cursor != null && !cursor.isEmpty()) {
            sql.append(" AND m.created_at < ?");
            params.add(cursor);
        }
        sql.append(" ORDER BY m.created_at DESC LIMIT ?");
        params.add(limit);
        return jdbc.query(sql.toString(), mapper, params.toArray());
    }

    private List<Memory> searchLike(
            String workspaceId, String query, List<String> tags, int limit, String cursor) {
        StringBuilder sql = new StringBuilder("SELECT * FROM memory WHERE workspace_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(workspaceId);
        if (query != null && !query.isEmpty()) {
            sql.append(" AND content LIKE ?");
            params.add("%" + query + "%");
        }
        appendTags(sql, params, tags, false);
        if (cursor != null && !cursor.isEmpty()) {
            sql.append(" AND created_at < ?");
            params.add(cursor);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);
        return jdbc.query(sql.toString(), mapper, params.toArray());
    }

    private static void appendTags(
            StringBuilder sql, List<Object> params, List<String> tags, boolean aliased) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        String col = aliased ? "m.tags" : "tags";
        sql.append(" AND (");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(col).append(" LIKE ?");
            params.add("%\"" + tags.get(i) + "\"%");
        }
        sql.append(")");
    }

    /** 将用户查询转为 FTS5 前缀表达式；特殊字符剥离 */
    static String toFtsMatch(String query) {
        String cleaned = query.replaceAll("[\"*():^\\[\\]{}]", " ").trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("empty FTS query");
        }
        StringBuilder sb = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('"').append(part).append('"').append('*');
        }
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("empty FTS query");
        }
        return sb.toString();
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
            jdbc.update(
                    "INSERT INTO memory (id, workspace_id, source, content, tags, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    memory.getId(), memory.getWorkspaceId(), memory.getSource(),
                    memory.getContent(), tagsJson, memory.getCreatedAt(), memory.getUpdatedAt());
            syncFtsInsert(memory);
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
            jdbc.update(
                    "UPDATE memory SET content = ?, tags = ?, updated_at = ? WHERE id = ?",
                    memory.getContent(), tagsJson, memory.getUpdatedAt(), memory.getId());
            syncFtsUpdate(memory);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update memory", e);
        }
        return memory;
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM memory WHERE id = ?", id);
        try {
            jdbc.update("DELETE FROM memory_fts WHERE id = ?", id);
        } catch (RuntimeException e) {
            log.debug("FTS delete skipped: {}", e.getMessage());
        }
    }

    private void syncFtsInsert(Memory memory) {
        try {
            jdbc.update(
                    "INSERT INTO memory_fts(id, workspace_id, content) VALUES (?, ?, ?)",
                    memory.getId(), memory.getWorkspaceId(), memory.getContent());
        } catch (RuntimeException e) {
            log.warn("FTS insert failed (search may fall back to LIKE): {}", e.getMessage());
        }
    }

    private void syncFtsUpdate(Memory memory) {
        try {
            jdbc.update("DELETE FROM memory_fts WHERE id = ?", memory.getId());
            syncFtsInsert(memory);
        } catch (RuntimeException e) {
            log.warn("FTS update failed: {}", e.getMessage());
        }
    }
}
