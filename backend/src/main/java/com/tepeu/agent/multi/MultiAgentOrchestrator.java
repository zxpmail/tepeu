package com.tepeu.agent.multi;

import com.tepeu.agent.tool.DeleteFileTool;
import com.tepeu.agent.tool.ListDirTool;
import com.tepeu.agent.tool.ReadFileTool;
import com.tepeu.agent.tool.ReadOutputTool;
import com.tepeu.agent.tool.RunCommandTool;
import com.tepeu.agent.tool.SearchFileTool;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.WorkspaceBoundTool;
import com.tepeu.agent.tool.WriteFileTool;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import com.tepeu.service.TokenCostEstimator;
import com.tepeu.service.chat.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Planner → Implementer → Reviewer 顺序协作；共享工作区与工具/Hook 执行面。
 * Reviewer 仅暴露只读工具白名单。
 * 关联：ChatService、MultiAgentController、Goal、AgentRolePrompts。
 */
@Component
public class MultiAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);

    /** Reviewer 代码级只读工具面（不含写/删/shell/MCP） */
    static final Set<String> REVIEWER_TOOLS = Set.of(
            "list_files", "read_file", "search_files", "read_output");

    private final ChatService chatService;
    private final AgentRolePrompts rolePrompts;
    private final SessionService sessionService;
    private final TaskService taskService;
    private final TokenCostEstimator costEstimator;
    private final ListDirTool listDirTool;
    private final ReadFileTool readFileTool;
    private final WriteFileTool writeFileTool;
    private final SearchFileTool searchFileTool;
    private final DeleteFileTool deleteFileTool;
    private final RunCommandTool runCommandTool;
    private final ReadOutputTool readOutputTool;

    public MultiAgentOrchestrator(
            ChatService chatService,
            AgentRolePrompts rolePrompts,
            SessionService sessionService,
            TaskService taskService,
            TokenCostEstimator costEstimator,
            ListDirTool listDirTool,
            ReadFileTool readFileTool,
            WriteFileTool writeFileTool,
            SearchFileTool searchFileTool,
            DeleteFileTool deleteFileTool,
            RunCommandTool runCommandTool,
            ReadOutputTool readOutputTool) {
        this.chatService = chatService;
        this.rolePrompts = rolePrompts;
        this.sessionService = sessionService;
        this.taskService = taskService;
        this.costEstimator = costEstimator;
        this.listDirTool = listDirTool;
        this.readFileTool = readFileTool;
        this.writeFileTool = writeFileTool;
        this.searchFileTool = searchFileTool;
        this.deleteFileTool = deleteFileTool;
        this.runCommandTool = runCommandTool;
        this.readOutputTool = readOutputTool;
    }

    /**
     * 同步跑完流水线；通过 {@code emit} 推送 SSE 事件 Map。
     * @return 最终状态 succeeded | failed
     */
    public String run(
            String providerId,
            Goal goal,
            String workspaceId,
            String sessionId,
            ToolEventEmitter toolEmitter,
            Consumer<Map<String, Object>> emit) {
        // 与单 Agent / 自主调度共用进程级绑定锁，防止工具交叉绑定工作区
        WorkspaceBoundTool.BIND_LOCK.lock();
        try {
            return runLocked(providerId, goal, workspaceId, sessionId, toolEmitter, emit);
        } finally {
            WorkspaceBoundTool.BIND_LOCK.unlock();
        }
    }

    private String runLocked(
            String providerId,
            Goal goal,
            String workspaceId,
            String sessionId,
            ToolEventEmitter toolEmitter,
            Consumer<Map<String, Object>> emit) {
        String runId = UUID.randomUUID().toString();
        emit.accept(event("multi_agent_start", Map.of(
                "runId", runId,
                "goal", goal.objective(),
                "acceptanceCriteria", goal.acceptanceCriteria())));

        listDirTool.bindWorkspace(workspaceId);
        readFileTool.bindWorkspace(workspaceId);
        writeFileTool.bindWorkspace(workspaceId);
        searchFileTool.bindWorkspace(workspaceId);
        deleteFileTool.bindWorkspace(workspaceId);
        runCommandTool.bindWorkspace(workspaceId);
        runCommandTool.bindSession(sessionId);
        readOutputTool.bindSession(sessionId);
        try {
            String plan = runRole(AgentRole.PLANNER, providerId, goal, null, null,
                    false, workspaceId, sessionId, toolEmitter, emit);
            if (plan == null) {
                emit.accept(finalEvent(runId, "failed"));
                return "failed";
            }

            String impl = runRole(AgentRole.IMPLEMENTER, providerId, goal, plan, null,
                    true, workspaceId, sessionId, toolEmitter, emit);
            if (impl == null) {
                emit.accept(finalEvent(runId, "failed"));
                return "failed";
            }

            // Reviewer：只读工具白名单核验
            String review = runRole(AgentRole.REVIEWER, providerId, goal, plan, impl,
                    true, workspaceId, sessionId, toolEmitter, emit);
            if (review == null) {
                emit.accept(finalEvent(runId, "failed"));
                return "failed";
            }

            boolean pass = VerdictParser.isPass(review);
            if (!pass) {
                emit.accept(event("agent_role_failed", Map.of(
                        "role", AgentRole.REVIEWER.name(),
                        "reason", "审查未通过（VERDICT 不是 PASS）",
                        "content", truncate(review, 2000))));
                emit.accept(finalEvent(runId, "failed"));
                return "failed";
            }

            emit.accept(finalEvent(runId, "succeeded"));
            return "succeeded";
        } finally {
            listDirTool.unbindWorkspace();
            readFileTool.unbindWorkspace();
            writeFileTool.unbindWorkspace();
            searchFileTool.unbindWorkspace();
            deleteFileTool.unbindWorkspace();
            runCommandTool.unbindWorkspace();
            runCommandTool.unbindSession();
            readOutputTool.unbindSession();
        }
    }

    /**
     * @return 角色输出文本；失败返回 null（已发 agent_role_failed）
     */
    private String runRole(
            AgentRole role,
            String providerId,
            Goal goal,
            String plan,
            String implementerReport,
            boolean withTools,
            String workspaceId,
            String sessionId,
            ToolEventEmitter toolEmitter,
            Consumer<Map<String, Object>> emit) {
        emit.accept(event("agent_role_start", Map.of("role", role.name())));
        try {
            String system = switch (role) {
                case PLANNER -> rolePrompts.planner(goal);
                case IMPLEMENTER -> rolePrompts.implementer(goal, plan == null ? "" : plan);
                case REVIEWER -> rolePrompts.reviewer(
                        goal, plan == null ? "" : plan, implementerReport == null ? "" : implementerReport);
            };
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(system),
                    new UserMessage("开始执行你的角色任务。")));

            ChatService.TurnResult turn;
            if (withTools) {
                Set<String> allowed = role == AgentRole.REVIEWER ? REVIEWER_TOOLS : null;
                turn = chatService.chatWithToolsTurn(providerId, prompt, toolEmitter, sessionId, allowed);
            } else {
                turn = chatService.chatTurn(providerId, prompt);
            }
            String text = turn.text() == null ? "" : turn.text();
            recordUsage(workspaceId, sessionId, providerId, turn);
            appendRoleMessage(sessionId, role, text);

            emit.accept(event("agent_role_done", Map.of(
                    "role", role.name(),
                    "ok", true,
                    "content", truncate(text, 4000))));
            return text;
        } catch (RuntimeException e) {
            log.warn("Multi-agent role {} failed: {}", role, e.toString());
            emit.accept(event("agent_role_failed", Map.of(
                    "role", role.name(),
                    "reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            return null;
        }
    }

    private void recordUsage(
            String workspaceId, String sessionId, String providerId, ChatService.TurnResult turn) {
        int prompt = turn.promptTokens();
        int completion = turn.completionTokens();
        int total = prompt + completion;
        if (workspaceId == null || total <= 0) {
            return;
        }
        try {
            double cost = costEstimator.estimate(providerId, prompt, completion);
            taskService.recordTurn(workspaceId, sessionId, providerId, prompt, completion, cost);
        } catch (RuntimeException e) {
            log.debug("Failed to record multi-agent usage: {}", e.getMessage());
        }
    }

    private void appendRoleMessage(String sessionId, AgentRole role, String text) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            sessionService.appendMessage(sessionId, "assistant",
                    "[Multi-Agent " + role.name() + "]\n" + truncate(text, 8000));
        } catch (RuntimeException e) {
            log.debug("Failed to append role message: {}", e.getMessage());
        }
    }

    private static Map<String, Object> event(String type, Map<String, ?> fields) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.putAll(fields);
        return m;
    }

    private static Map<String, Object> finalEvent(String runId, String status) {
        return event("multi_agent_final", Map.of("runId", runId, "status", status));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
