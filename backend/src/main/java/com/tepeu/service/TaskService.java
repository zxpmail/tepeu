package com.tepeu.service;

import com.tepeu.model.Task;
import com.tepeu.repository.MessageRepository;
import com.tepeu.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 记录每次对话回合的 token/费用，并聚合会话级统计。
 * 与 {@link com.tepeu.agent.AgentOrchestrator} 的历史窗口上限对齐（maxHistoryMessages=50）。
 */
@Service
public class TaskService {

    /** 与 AgentOrchestrator.MAX_HISTORY_MESSAGES 保持一致。 */
    static final int MAX_HISTORY_MESSAGES = 50;

    private final TaskRepository taskRepository;
    private final MessageRepository messageRepository;

    public TaskService(TaskRepository taskRepository, MessageRepository messageRepository) {
        this.taskRepository = taskRepository;
        this.messageRepository = messageRepository;
    }

    /** 会话统计：token 合计、费用、回合数、消息数、历史窗口上限。 */
    public record SessionStats(
            long totalTokens,
            double totalCostUsd,
            int turnCount,
            int messageCount,
            int maxHistoryMessages) {}

    /** 记录一次完成的对话回合（prompt+completion token 合计写入 tokens_used）。 */
    public Task recordTurn(
            String workspaceId,
            String sessionId,
            String modelUsed,
            int promptTokens,
            int completionTokens,
            double costUsd) {
        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setWorkspaceId(workspaceId);
        task.setSessionId(sessionId);
        task.setStatus("completed");
        task.setOutcome("succeeded");
        task.setModelUsed(modelUsed);
        task.setTokensUsed(promptTokens + completionTokens);
        task.setCostUsd(costUsd);
        task.setStartedAt(now);
        task.setCompletedAt(now);
        return taskRepository.save(task);
    }

    /** 聚合会话 token/费用，并附带消息条数与历史窗口上限。 */
    public SessionStats getSessionStats(String sessionId) {
        TaskRepository.SessionTokenStats agg = taskRepository.findSessionStats(sessionId);
        int messageCount = messageRepository.countBySessionId(sessionId);
        return new SessionStats(
                agg.totalTokens(),
                agg.totalCostUsd(),
                agg.turnCount(),
                messageCount,
                MAX_HISTORY_MESSAGES);
    }
}
