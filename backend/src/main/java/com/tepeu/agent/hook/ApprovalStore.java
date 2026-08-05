package com.tepeu.agent.hook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 会话级工具审批：pending + 按「工具+参数」的会话授权。
 * approval-wait-seconds &gt; 0 时 Hook 可阻塞等待用户裁决。
 * 关联：HookingToolCallback、ApprovalController。
 */
@Component
public class ApprovalStore {

    public enum Status {
        PENDING,
        APPROVED,
        DENIED,
        /** 等待超时，未授权 */
        EXPIRED
    }

    public record Approval(
            String id,
            String sessionId,
            String tool,
            String argsJson,
            Status status,
            Instant createdAt) {}

    private final Map<String, Approval> byId = new ConcurrentHashMap<>();
    /** sessionId → grantKey(tool+args) 集合 */
    private final Map<String, Set<String>> sessionGrants = new ConcurrentHashMap<>();
    private final Set<String> autonomousSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, CountDownLatch> waitLatches = new ConcurrentHashMap<>();
    private final Duration approvalWait;

    @Autowired
    public ApprovalStore(@Value("${tepeu.hook.approval-wait-seconds:540}") long waitSeconds) {
        this.approvalWait = Duration.ofSeconds(Math.max(0, waitSeconds));
    }

    /** 等待时长；0 表示 Hook 不阻塞，立即返回 APPROVAL_REQUIRED。 */
    public Duration approvalWait() {
        return approvalWait;
    }

    /**
     * 创建 pending。同会话同工具同参数若已有 PENDING 则复用。
     */
    public Approval createPending(String sessionId, String tool, String argsJson) {
        String sid = sessionId == null ? "" : sessionId;
        String toolName = tool == null ? "" : tool;
        String args = normalizeArgs(argsJson);
        Optional<Approval> existing = findPending(sid, toolName, args);
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = UUID.randomUUID().toString();
        Approval a = new Approval(id, sid, toolName, args, Status.PENDING, Instant.now());
        byId.put(id, a);
        waitLatches.put(id, new CountDownLatch(1));
        return a;
    }

    /** 兼容旧调用：仅按工具查找任意 PENDING。 */
    public Optional<Approval> findPending(String sessionId, String tool) {
        if (sessionId == null || tool == null) {
            return Optional.empty();
        }
        return byId.values().stream()
                .filter(a -> a.status() == Status.PENDING
                        && sessionId.equals(a.sessionId())
                        && tool.equals(a.tool()))
                .findFirst();
    }

    /** 同会话同工具同参数的 PENDING。 */
    public Optional<Approval> findPending(String sessionId, String tool, String argsJson) {
        if (sessionId == null || tool == null) {
            return Optional.empty();
        }
        String args = normalizeArgs(argsJson);
        return byId.values().stream()
                .filter(a -> a.status() == Status.PENDING
                        && sessionId.equals(a.sessionId())
                        && tool.equals(a.tool())
                        && args.equals(normalizeArgs(a.argsJson())))
                .findFirst();
    }

    public Optional<Approval> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** 当前会话是否已批准该工具+参数组合。 */
    public boolean hasSessionGrant(String sessionId, String tool, String argsJson) {
        if (sessionId == null || sessionId.isBlank() || tool == null) {
            return false;
        }
        Set<String> grants = sessionGrants.get(sessionId);
        return grants != null && grants.contains(grantKey(tool, argsJson));
    }

    public void enableAutonomous(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            autonomousSessions.add(sessionId);
        }
    }

    /** 撤销自主免批（调度运行结束调用），防止会话被续用时 shell 永久绕过审批。 */
    public void disableAutonomous(String sessionId) {
        if (sessionId != null) {
            autonomousSessions.remove(sessionId);
        }
    }

    public boolean isAutonomous(String sessionId) {
        return sessionId != null && !sessionId.isBlank() && autonomousSessions.contains(sessionId);
    }

    /**
     * 用户裁决：approve → 按工具+参数写入会话授权；deny → 不授权。
     */
    public Optional<Approval> decide(String id, boolean approve) {
        Approval current = byId.get(id);
        if (current == null || current.status() != Status.PENDING) {
            return Optional.empty();
        }
        Approval next = new Approval(
                current.id(),
                current.sessionId(),
                current.tool(),
                current.argsJson(),
                approve ? Status.APPROVED : Status.DENIED,
                current.createdAt());
        byId.put(id, next);
        if (approve && current.sessionId() != null && !current.sessionId().isBlank()) {
            sessionGrants
                    .computeIfAbsent(current.sessionId(), k -> ConcurrentHashMap.newKeySet())
                    .add(grantKey(current.tool(), current.argsJson()));
        }
        CountDownLatch latch = waitLatches.remove(id);
        if (latch != null) {
            latch.countDown();
        }
        return Optional.of(next);
    }

    /**
     * 阻塞等待裁决。
     * @return APPROVED / DENIED；超时标记 EXPIRED 后返回 empty；中断返回 empty
     */
    public Optional<Status> awaitDecision(String id) {
        if (approvalWait.isZero()) {
            return Optional.empty();
        }
        Approval current = byId.get(id);
        if (current != null && current.status() != Status.PENDING) {
            return Optional.of(current.status());
        }
        CountDownLatch latch = waitLatches.get(id);
        if (latch == null) {
            Approval again = byId.get(id);
            return again == null || again.status() == Status.PENDING
                    ? Optional.empty()
                    : Optional.of(again.status());
        }
        try {
            boolean done = latch.await(approvalWait.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                expire(id);
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            expire(id);
            return Optional.empty();
        }
        Approval decided = byId.get(id);
        if (decided == null || decided.status() == Status.PENDING) {
            return Optional.empty();
        }
        return Optional.of(decided.status());
    }

    /** 超时或中断：结束 PENDING，释放等待者。 */
    public void expire(String id) {
        Approval current = byId.get(id);
        if (current != null && current.status() == Status.PENDING) {
            byId.put(id, new Approval(
                    current.id(),
                    current.sessionId(),
                    current.tool(),
                    current.argsJson(),
                    Status.EXPIRED,
                    current.createdAt()));
        }
        CountDownLatch latch = waitLatches.remove(id);
        if (latch != null) {
            latch.countDown();
        }
    }

    static String grantKey(String tool, String argsJson) {
        return (tool == null ? "" : tool) + "\n" + normalizeArgs(argsJson);
    }

    static String normalizeArgs(String argsJson) {
        return argsJson == null ? "" : argsJson.trim();
    }

    void clear() {
        byId.clear();
        sessionGrants.clear();
        autonomousSessions.clear();
        waitLatches.clear();
    }
}
