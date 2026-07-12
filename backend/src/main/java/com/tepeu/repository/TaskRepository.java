package com.tepeu.repository;

import com.tepeu.model.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcTemplate CRUD for {@link Task}. Aggregates per-session token/cost stats for the stats API.
 */
@Repository
public class TaskRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Task> mapper;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = (rs, rowNum) -> {
            Task t = new Task();
            t.setId(rs.getString("id"));
            t.setWorkspaceId(rs.getString("workspace_id"));
            t.setSessionId(rs.getString("session_id"));
            t.setStatus(rs.getString("status"));
            t.setOutcome(rs.getString("outcome"));
            t.setModelUsed(rs.getString("model_used"));
            int tokens = rs.getInt("tokens_used");
            t.setTokensUsed(rs.wasNull() ? null : tokens);
            double cost = rs.getDouble("cost_usd");
            t.setCostUsd(rs.wasNull() ? null : cost);
            t.setStartedAt(rs.getObject("started_at", LocalDateTime.class));
            t.setCompletedAt(rs.getObject("completed_at", LocalDateTime.class));
            return t;
        };
    }

    /** 会话级聚合：总 token、总费用、回合数。 */
    public record SessionTokenStats(long totalTokens, double totalCostUsd, int turnCount) {}

    public Task save(Task task) {
        if (task.getId() == null) {
            task.setId(UUID.randomUUID().toString());
        }
        jdbc.update(
                """
                INSERT INTO task (id, workspace_id, session_id, status, outcome, model_used,
                                  tokens_used, cost_usd, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                task.getId(), task.getWorkspaceId(), task.getSessionId(),
                task.getStatus(), task.getOutcome(), task.getModelUsed(),
                task.getTokensUsed(), task.getCostUsd(),
                task.getStartedAt(), task.getCompletedAt());
        return task;
    }

    public Optional<Task> findById(String id) {
        List<Task> results = jdbc.query("SELECT * FROM task WHERE id = ?", mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** 按 session_id 汇总 tokens / cost / 回合数。 */
    public SessionTokenStats findSessionStats(String sessionId) {
        return jdbc.query(
                """
                SELECT COALESCE(SUM(tokens_used), 0) AS total_tokens,
                       COALESCE(SUM(cost_usd), 0.0) AS total_cost,
                       COUNT(*) AS turn_count
                FROM task WHERE session_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return new SessionTokenStats(0L, 0.0, 0);
                    }
                    return new SessionTokenStats(
                            rs.getLong("total_tokens"),
                            rs.getDouble("total_cost"),
                            rs.getInt("turn_count"));
                },
                sessionId);
    }
}
