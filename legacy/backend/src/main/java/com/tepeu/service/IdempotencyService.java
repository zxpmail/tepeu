package com.tepeu.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 请求层幂等：Agent/客户端生成 key，本服务判断是否重复执行。
 * 关联：ChatController（可选 idempotencyKey）。内存实现，TTL 24h；非分布式。
 */
@Service
public class IdempotencyService {

    public static final Duration TTL = Duration.ofHours(24);

    public enum State { IN_PROGRESS, COMPLETED }

    public record Entry(State state, String resultText, Instant createdAt) {}

    public enum AcquireStatus {
        /** 首次占用，调用方应执行业务 */
        EXECUTE,
        /** 已有完成结果，调用方应直接回放 */
        REPLAY,
        /** 同 key 正在执行 */
        IN_PROGRESS
    }

    public record AcquireResult(AcquireStatus status, String cachedText) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    /** 尝试占用 key；过期条目会被清理后重新占用。 */
    public AcquireResult tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return new AcquireResult(AcquireStatus.EXECUTE, null);
        }
        String k = key.trim();
        Instant now = Instant.now();
        AtomicReference<AcquireStatus> status = new AtomicReference<>();
        AtomicReference<String> cached = new AtomicReference<>();

        store.compute(k, (ignored, existing) -> {
            if (existing == null || isExpired(existing, now)) {
                status.set(AcquireStatus.EXECUTE);
                return new Entry(State.IN_PROGRESS, null, now);
            }
            if (existing.state() == State.COMPLETED) {
                status.set(AcquireStatus.REPLAY);
                cached.set(existing.resultText());
                return existing;
            }
            status.set(AcquireStatus.IN_PROGRESS);
            return existing;
        });

        return new AcquireResult(status.get(), cached.get());
    }

    /** 执行成功后写入可回放结果。 */
    public void complete(String key, String resultText) {
        if (key == null || key.isBlank()) return;
        store.put(key.trim(), new Entry(State.COMPLETED, resultText == null ? "" : resultText, Instant.now()));
    }

    /** 执行失败时释放占用，允许重试。 */
    public void release(String key) {
        if (key == null || key.isBlank()) return;
        store.computeIfPresent(key.trim(), (k, e) -> e.state() == State.IN_PROGRESS ? null : e);
    }

    public Optional<Entry> peek(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        String k = key.trim();
        Instant now = Instant.now();
        store.computeIfPresent(k, (ignored, e) -> isExpired(e, now) ? null : e);
        return Optional.ofNullable(store.get(k));
    }

    /** 单测用：清空。 */
    void clear() {
        store.clear();
    }

    private static boolean isExpired(Entry e, Instant now) {
        return e.createdAt().plus(TTL).isBefore(now);
    }
}
