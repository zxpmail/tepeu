package com.tepeu.agent.slash;

import com.tepeu.agent.slash.commands.CompactCommand;
import com.tepeu.agent.slash.commands.HelpCommand;
import com.tepeu.model.AgentSchedule;
import com.tepeu.model.Workspace;
import com.tepeu.service.BudgetService;
import com.tepeu.service.ScheduleService;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import com.tepeu.service.WorkspaceService;
import com.tepeu.agent.slash.commands.ScheduleCommand;
import com.tepeu.agent.slash.commands.StatusCommand;
import com.tepeu.agent.slash.commands.TasksCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Slash 注册表与首批命令（不调 LLM）。 */
class SlashCommandRegistryTest {

    private SlashCommandRegistry registry;
    private ScheduleService scheduleService;
    private TaskService taskService;
    private BudgetService budgetService;
    private WorkspaceService workspaceService;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        scheduleService = mock(ScheduleService.class);
        taskService = mock(TaskService.class);
        budgetService = mock(BudgetService.class);
        workspaceService = mock(WorkspaceService.class);
        sessionService = mock(SessionService.class);

        // Help 需要 Lazy registry：先建临时，再重建含 Help 的完整表
        CompactCommand compact = new CompactCommand();
        TasksCommand tasks = new TasksCommand(taskService);
        ScheduleCommand schedule = new ScheduleCommand(scheduleService);
        StatusCommand status = new StatusCommand(workspaceService, budgetService, sessionService);
        HelpCommand help = new HelpCommand();

        registry = new SlashCommandRegistry(List.of(help, compact, tasks, schedule, status));
        help.setRegistry(registry);
    }

    @Test
    void help_listsCommands() {
        SlashResult r = registry.execute("help", new SlashContext(null, null, List.of()));
        assertTrue(r.text().contains("/help"));
        assertTrue(r.text().contains("/schedule"));
        assertTrue(r.text().contains("/tasks"));
        assertNull(r.action());
    }

    @Test
    void scheduleList_returnsSchedules() {
        AgentSchedule s = new AgentSchedule();
        s.setName("日报");
        s.setIntervalMinutes(60);
        s.setEnabled(true);
        s.setLastStatus("SUCCESS");
        when(scheduleService.list("ws-1")).thenReturn(List.of(s));

        SlashResult r = registry.execute("schedule", new SlashContext("ws-1", null, List.of("list")));
        assertTrue(r.text().contains("日报"));
        assertTrue(r.text().contains("SUCCESS"));
    }

    @Test
    void schedule_requiresWorkspace() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> registry.execute("schedule", new SlashContext(null, null, List.of())));
        assertTrue(ex.getMessage().contains("工作区"));
    }

    @Test
    void tasks_summarizesUsage() {
        when(taskService.getWorkspaceStats("ws-1"))
                .thenReturn(new TaskService.WorkspaceStats(1200L, 0.12, 3));
        SlashResult r = registry.execute("tasks", new SlashContext("ws-1", null, List.of()));
        assertTrue(r.text().contains("1200"));
        assertTrue(r.text().contains("3"));
    }

    @Test
    void status_includesWorkspaceName() {
        when(workspaceService.getWorkspace("ws-1"))
                .thenReturn(Optional.of(new Workspace("ws-1", "演示区", null, "personal", "local")));
        when(budgetService.status("ws-1")).thenReturn(new BudgetService.BudgetStatus(
                "ws-1", 100L, 0.01, 2, null, false, 0.8, 0, false, false));
        when(sessionService.listSessions("ws-1")).thenReturn(List.of());

        SlashResult r = registry.execute("status", new SlashContext("ws-1", "sess-1", List.of()));
        assertTrue(r.text().contains("演示区"));
        assertTrue(r.text().contains("sess-1"));
    }

    @Test
    void compact_returnsAction() {
        SlashResult r = registry.execute("compact", new SlashContext(null, null, List.of()));
        assertEquals("compact", r.action());
        assertNotNull(r.text());
    }

    @Test
    void unknownCommand_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("nope", new SlashContext("ws-1", null, List.of())));
    }
}
