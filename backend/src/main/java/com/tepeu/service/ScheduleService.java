package com.tepeu.service;

import com.tepeu.agent.AgentOrchestrator;
import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.config.LlmProviderConfig;
import com.tepeu.model.AgentSchedule;
import com.tepeu.model.Message;
import com.tepeu.model.Session;
import com.tepeu.repository.AgentScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 自主 Agent 定时调度（Spec M3.1）。
 * 到期任务新建会话，走既有 AgentOrchestrator + 工具面；自主会话跳过人工审批并记账成本。
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final int MIN_INTERVAL = 1;
    private static final int MAX_INTERVAL = 10080; // 7 天

    private final AgentScheduleRepository repository;
    private final WorkspaceService workspaceService;
    private final SessionService sessionService;
    private final BudgetService budgetService;
    private final AgentOrchestrator orchestrator;
    private final ApprovalStore approvalStore;
    private final TaskService taskService;
    private final TokenCostEstimator costEstimator;
    private final LlmProviderConfig providerConfig;
    private final boolean tickerEnabled;
    private final int staleRunningMinutes;
    /** 防止同一任务并发重入 */
    private final ConcurrentHashMap<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public ScheduleService(
            AgentScheduleRepository repository,
            WorkspaceService workspaceService,
            SessionService sessionService,
            BudgetService budgetService,
            AgentOrchestrator orchestrator,
            ApprovalStore approvalStore,
            TaskService taskService,
            TokenCostEstimator costEstimator,
            LlmProviderConfig providerConfig,
            @Value("${tepeu.schedule.enabled:true}") boolean tickerEnabled,
            @Value("${tepeu.schedule.stale-running-minutes:30}") int staleRunningMinutes) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.sessionService = sessionService;
        this.budgetService = budgetService;
        this.orchestrator = orchestrator;
        this.approvalStore = approvalStore;
        this.taskService = taskService;
        this.costEstimator = costEstimator;
        this.providerConfig = providerConfig;
        this.tickerEnabled = tickerEnabled;
        this.staleRunningMinutes = Math.max(5, staleRunningMinutes);
    }

    public List<AgentSchedule> list(String workspaceId) {
        requireWorkspace(workspaceId);
        return repository.findByWorkspaceId(workspaceId);
    }

    public Optional<AgentSchedule> get(String id) {
        return repository.findById(id);
    }

    public AgentSchedule create(
            String workspaceId, String name, String prompt, String providerId,
            int intervalMinutes, boolean enabled) {
        requireWorkspace(workspaceId);
        String n = normalizeName(name);
        String p = requirePrompt(prompt);
        String prov = requireProvider(providerId);
        int interval = normalizeInterval(intervalMinutes);

        AgentSchedule s = new AgentSchedule();
        s.setWorkspaceId(workspaceId);
        s.setName(n);
        s.setPrompt(p);
        s.setProviderId(prov);
        s.setIntervalMinutes(interval);
        s.setEnabled(enabled);
        // 启用后尽快可被下一 tick 拾取（不必再空等一个完整间隔）
        s.setNextRunAt(enabled ? LocalDateTime.now() : null);
        s.setLastStatus("IDLE");
        return repository.insert(s);
    }

    public Optional<AgentSchedule> update(String id, String name, String prompt, String providerId,
                                          Integer intervalMinutes, Boolean enabled) {
        Optional<AgentSchedule> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        AgentSchedule s = opt.get();
        if (name != null) {
            s.setName(normalizeName(name));
        }
        if (prompt != null) {
            s.setPrompt(requirePrompt(prompt));
        }
        if (providerId != null) {
            s.setProviderId(requireProvider(providerId));
        }
        if (intervalMinutes != null) {
            s.setIntervalMinutes(normalizeInterval(intervalMinutes));
        }
        if (enabled != null) {
            s.setEnabled(enabled);
            if (enabled) {
                if (s.getNextRunAt() == null || "RUNNING".equals(s.getLastStatus())) {
                    s.setNextRunAt(LocalDateTime.now());
                }
                if ("RUNNING".equals(s.getLastStatus())) {
                    s.setLastStatus("IDLE");
                }
            } else {
                s.setNextRunAt(null);
            }
        }
        return repository.update(s);
    }

    public boolean delete(String id) {
        return repository.delete(id);
    }

    /**
     * 手动立即运行：先落库 RUNNING 再异步执行，避免 API 返回旧状态。
     */
    public AgentSchedule runNow(String id) {
        AgentSchedule s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        if ("RUNNING".equals(s.getLastStatus()) || isLocked(id)) {
            throw new IllegalStateException("Schedule is already running");
        }
        AtomicBoolean flag = running.computeIfAbsent(id, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            throw new IllegalStateException("Schedule is already running");
        }
        try {
            markRunning(s);
            Thread.startVirtualThread(() -> {
                try {
                    runBody(id, true);
                } finally {
                    flag.set(false);
                }
            });
            return repository.findById(id).orElse(s);
        } catch (RuntimeException e) {
            flag.set(false);
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "${tepeu.schedule.tick-ms:30000}", initialDelayString = "${tepeu.schedule.initial-delay-ms:15000}")
    public void tick() {
        if (!tickerEnabled) {
            return;
        }
        recoverStaleRunning();
        List<AgentSchedule> due = repository.findDue(LocalDateTime.now());
        for (AgentSchedule s : due) {
            Thread.startVirtualThread(() -> execute(s.getId(), false));
        }
    }

    /** 供单测：同步执行一轮（含锁）。 */
    void execute(String id, boolean manual) {
        AtomicBoolean flag = running.computeIfAbsent(id, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            log.debug("Schedule {} already locked, skip", id);
            return;
        }
        try {
            runBody(id, manual);
        } finally {
            flag.set(false);
        }
    }

    /** 已持锁时的执行体；manual 时允许入口处已是 RUNNING。 */
    private void runBody(String id, boolean manual) {
        Optional<AgentSchedule> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return;
        }
        AgentSchedule s = opt.get();
        if (!manual && !s.isEnabled()) {
            return;
        }
        if (!manual && "RUNNING".equals(s.getLastStatus())) {
            return;
        }

        if (!"RUNNING".equals(s.getLastStatus())) {
            markRunning(s);
        }

        if (budgetService.isBlocked(s.getWorkspaceId())) {
            fail(s, "工作区预算已用尽，已暂停调度");
            return;
        }

        String title = "自主 · " + s.getName();
        if (title.length() > 80) {
            title = title.substring(0, 80);
        }
        Session session = sessionService.createSession(s.getWorkspaceId(), title);
        sessionService.appendMessage(session.getId(), "user", s.getPrompt());
        List<Message> history = sessionService.listMessages(session.getId());
        approvalStore.enableAutonomous(session.getId());

        StringBuilder assistant = new StringBuilder();
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        AtomicReference<String> lastModel = new AtomicReference<>();
        try {
            List<ChatResponse> chunks = orchestrator
                    .streamTurn(s.getProviderId(), history, ToolEventEmitter.NOOP,
                            null, s.getWorkspaceId(), null, session.getId())
                    .collectList()
                    .block();
            if (chunks != null) {
                for (ChatResponse chunk : chunks) {
                    captureUsage(chunk, lastUsage, lastModel);
                    String t = extractText(chunk);
                    if (t != null && !t.isEmpty()) {
                        assistant.append(t);
                    }
                }
            }
            recordUsage(s, session.getId(), lastUsage.get(), lastModel.get());

            String reply = assistant.toString();
            s.setLastSessionId(session.getId());
            if (reply.isBlank()) {
                s.setLastStatus("EMPTY");
                s.setLastError("Model returned empty response");
                s.setUpdatedAt(LocalDateTime.now());
                repository.update(s);
                log.warn("Schedule {} finished with empty reply session={}", id, session.getId());
                return;
            }
            sessionService.appendMessage(session.getId(), "assistant", reply);
            s.setLastStatus("SUCCESS");
            s.setLastError(null);
            s.setUpdatedAt(LocalDateTime.now());
            repository.update(s);
            log.info("Schedule {} finished session={}", id, session.getId());
        } catch (RuntimeException e) {
            log.warn("Schedule {} failed: {}", id, e.toString());
            s.setLastSessionId(session.getId());
            fail(s, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private void markRunning(AgentSchedule s) {
        LocalDateTime now = LocalDateTime.now();
        s.setLastStatus("RUNNING");
        s.setLastError(null);
        s.setLastRunAt(now);
        s.setNextRunAt(now.plusMinutes(s.getIntervalMinutes()));
        s.setUpdatedAt(now);
        repository.update(s);
    }

    /** 将卡死的 RUNNING 标为失败并重新排队 */
    void recoverStaleRunning() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleRunningMinutes);
        for (AgentSchedule s : repository.findStaleRunning(cutoff)) {
            log.warn("Recovering stale RUNNING schedule {} lastRunAt={}", s.getId(), s.getLastRunAt());
            running.remove(s.getId());
            fail(s, "Recovered: stuck in RUNNING (interrupted or timeout after "
                    + staleRunningMinutes + "m)");
        }
    }

    private void recordUsage(AgentSchedule s, String sessionId, Usage usage, String model) {
        if (usage == null) {
            return;
        }
        int prompt = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completion = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int total = usage.getTotalTokens() != null ? usage.getTotalTokens() : prompt + completion;
        if (total <= 0) {
            return;
        }
        double cost = costEstimator.estimate(s.getProviderId(), prompt, completion);
        try {
            taskService.recordTurn(
                    s.getWorkspaceId(),
                    sessionId,
                    model != null ? model : s.getProviderId(),
                    prompt,
                    completion,
                    cost);
        } catch (RuntimeException e) {
            log.debug("Failed to record schedule usage: {}", e.getMessage());
        }
    }

    private static void captureUsage(ChatResponse chunk, AtomicReference<Usage> lastUsage,
                                     AtomicReference<String> lastModel) {
        if (chunk == null || chunk.getMetadata() == null) return;
        Usage usage = chunk.getMetadata().getUsage();
        if (usage != null) {
            Integer total = usage.getTotalTokens();
            if (total != null && total > 0) {
                lastUsage.set(usage);
            } else if (usage.getPromptTokens() != null || usage.getCompletionTokens() != null) {
                lastUsage.set(usage);
            }
        }
        String model = chunk.getMetadata().getModel();
        if (model != null && !model.isBlank()) {
            lastModel.set(model);
        }
    }

    private void fail(AgentSchedule s, String error) {
        String msg = error == null ? "unknown" : error;
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        s.setLastStatus("FAILED");
        s.setLastError(msg);
        s.setUpdatedAt(LocalDateTime.now());
        if (s.isEnabled()) {
            LocalDateTime next = s.getNextRunAt();
            if (next == null || !next.isAfter(LocalDateTime.now())) {
                s.setNextRunAt(LocalDateTime.now().plusMinutes(s.getIntervalMinutes()));
            }
        }
        repository.update(s);
    }

    private boolean isLocked(String id) {
        AtomicBoolean flag = running.get(id);
        return flag != null && flag.get();
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (workspaceService.getWorkspace(workspaceId).isEmpty()) {
            throw new IllegalArgumentException("工作区不存在：" + workspaceId);
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String n = name.trim();
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    private static String requirePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        String p = prompt.trim();
        if (p.length() > 8000) {
            throw new IllegalArgumentException("prompt too long (max 8000)");
        }
        return p;
    }

    private String requireProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId is required");
        }
        String id = providerId.trim();
        List<LlmProviderConfig.Provider> catalog = providerConfig.getProviders();
        boolean known = catalog != null && catalog.stream().anyMatch(p -> id.equals(p.getId()));
        if (!known) {
            throw new IllegalArgumentException("Unknown providerId: " + id);
        }
        return id;
    }

    private static int normalizeInterval(int intervalMinutes) {
        if (intervalMinutes < MIN_INTERVAL || intervalMinutes > MAX_INTERVAL) {
            throw new IllegalArgumentException(
                    "intervalMinutes must be between " + MIN_INTERVAL + " and " + MAX_INTERVAL);
        }
        return intervalMinutes;
    }

    private static String extractText(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return null;
        }
        return chunk.getResult().getOutput().getText();
    }
}
