package com.tepeu.repository;

import com.tepeu.model.AgentSchedule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * agent_schedule 持久化。
 * 关联：ScheduleService、DatabaseConfig。
 */
@Repository
public class AgentScheduleRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<AgentSchedule> mapper = (rs, rowNum) -> {
        AgentSchedule s = new AgentSchedule();
        s.setId(rs.getString("id"));
        s.setWorkspaceId(rs.getString("workspace_id"));
        s.setName(rs.getString("name"));
        s.setPrompt(rs.getString("prompt"));
        s.setProviderId(rs.getString("provider_id"));
        s.setEnabled(rs.getInt("enabled") != 0);
        s.setIntervalMinutes(rs.getInt("interval_minutes"));
        s.setNextRunAt(rs.getObject("next_run_at", LocalDateTime.class));
        s.setLastRunAt(rs.getObject("last_run_at", LocalDateTime.class));
        s.setLastStatus(rs.getString("last_status"));
        s.setLastError(rs.getString("last_error"));
        s.setLastSessionId(rs.getString("last_session_id"));
        s.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        s.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return s;
    };

    public AgentScheduleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AgentSchedule> findByWorkspaceId(String workspaceId) {
        return jdbc.query(
                "SELECT * FROM agent_schedule WHERE workspace_id = ? ORDER BY created_at DESC",
                mapper, workspaceId);
    }

    public Optional<AgentSchedule> findById(String id) {
        List<AgentSchedule> rows = jdbc.query("SELECT * FROM agent_schedule WHERE id = ?", mapper, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 到期且启用、未在 RUNNING 的任务 */
    public List<AgentSchedule> findDue(LocalDateTime now) {
        return jdbc.query(
                """
                SELECT * FROM agent_schedule
                WHERE enabled = 1
                  AND next_run_at IS NOT NULL
                  AND next_run_at <= ?
                  AND (last_status IS NULL OR last_status <> 'RUNNING')
                ORDER BY next_run_at ASC
                LIMIT 20
                """,
                mapper, now);
    }

    /** 卡在 RUNNING 且上次开始早于 cutoff 的任务（进程中断恢复） */
    public List<AgentSchedule> findStaleRunning(LocalDateTime cutoff) {
        return jdbc.query(
                """
                SELECT * FROM agent_schedule
                WHERE last_status = 'RUNNING'
                  AND last_run_at IS NOT NULL
                  AND last_run_at < ?
                ORDER BY last_run_at ASC
                LIMIT 50
                """,
                mapper, cutoff);
    }

    public AgentSchedule insert(AgentSchedule s) {
        if (s.getId() == null || s.getId().isBlank()) {
            s.setId(UUID.randomUUID().toString());
        }
        LocalDateTime now = LocalDateTime.now();
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        jdbc.update(
                """
                INSERT INTO agent_schedule (
                  id, workspace_id, name, prompt, provider_id, enabled, interval_minutes,
                  next_run_at, last_run_at, last_status, last_error, last_session_id,
                  created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                s.getId(), s.getWorkspaceId(), s.getName(), s.getPrompt(), s.getProviderId(),
                s.isEnabled() ? 1 : 0, s.getIntervalMinutes(),
                s.getNextRunAt(), s.getLastRunAt(), s.getLastStatus(), s.getLastError(),
                s.getLastSessionId(), s.getCreatedAt(), s.getUpdatedAt());
        return s;
    }

    public Optional<AgentSchedule> update(AgentSchedule s) {
        s.setUpdatedAt(LocalDateTime.now());
        int n = jdbc.update(
                """
                UPDATE agent_schedule SET
                  name = ?, prompt = ?, provider_id = ?, enabled = ?, interval_minutes = ?,
                  next_run_at = ?, last_run_at = ?, last_status = ?, last_error = ?,
                  last_session_id = ?, updated_at = ?
                WHERE id = ?
                """,
                s.getName(), s.getPrompt(), s.getProviderId(), s.isEnabled() ? 1 : 0,
                s.getIntervalMinutes(), s.getNextRunAt(), s.getLastRunAt(), s.getLastStatus(),
                s.getLastError(), s.getLastSessionId(), s.getUpdatedAt(), s.getId());
        return n > 0 ? findById(s.getId()) : Optional.empty();
    }

    public boolean delete(String id) {
        return jdbc.update("DELETE FROM agent_schedule WHERE id = ?", id) > 0;
    }
}
