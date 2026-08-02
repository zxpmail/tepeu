package com.tepeu.service;

import com.tepeu.repository.MessageRepository;
import com.tepeu.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TaskService 聚合逻辑单测（无 DB）：会话统计 + 工作区累计。
 */
class TaskServiceTest {

    private TaskRepository taskRepository;
    private MessageRepository messageRepository;
    private TaskService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        messageRepository = mock(MessageRepository.class);
        service = new TaskService(taskRepository, messageRepository);
    }

    @Test
    void getWorkspaceStats_aggregatesFromRepository() {
        when(taskRepository.findWorkspaceStats("ws-1"))
                .thenReturn(new TaskRepository.WorkspaceTokenStats(1200L, 0.042, 3));

        TaskService.WorkspaceStats stats = service.getWorkspaceStats("ws-1");

        assertEquals(1200L, stats.totalTokens());
        assertEquals(0.042, stats.totalCostUsd(), 1e-9);
        assertEquals(3, stats.turnCount());
        verify(taskRepository).findWorkspaceStats("ws-1");
    }

    @Test
    void getSessionStats_includesMessageCountAndHistoryCap() {
        when(taskRepository.findSessionStats("sess-1"))
                .thenReturn(new TaskRepository.SessionTokenStats(100L, 0.01, 2));
        when(messageRepository.countBySessionId("sess-1")).thenReturn(4);

        TaskService.SessionStats stats = service.getSessionStats("sess-1");

        assertEquals(100L, stats.totalTokens());
        assertEquals(0.01, stats.totalCostUsd(), 1e-9);
        assertEquals(2, stats.turnCount());
        assertEquals(4, stats.messageCount());
        assertEquals(50, stats.maxHistoryMessages());
    }
}
