package com.tepeu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 幂等服务：同 key 不重复执行，完成结果可回放。 */
class IdempotencyServiceTest {

    private IdempotencyService svc;

    @BeforeEach
    void setUp() {
        svc = new IdempotencyService();
    }

    @Test
    void firstAcquire_shouldExecute() {
        var r = svc.tryAcquire("k1");
        assertEquals(IdempotencyService.AcquireStatus.EXECUTE, r.status());
    }

    @Test
    void secondAcquireWhileInProgress_shouldBlock() {
        assertEquals(IdempotencyService.AcquireStatus.EXECUTE, svc.tryAcquire("k1").status());
        assertEquals(IdempotencyService.AcquireStatus.IN_PROGRESS, svc.tryAcquire("k1").status());
    }

    @Test
    void afterComplete_shouldReplaySameText() {
        svc.tryAcquire("k1");
        svc.complete("k1", "hello");
        var r = svc.tryAcquire("k1");
        assertEquals(IdempotencyService.AcquireStatus.REPLAY, r.status());
        assertEquals("hello", r.cachedText());
    }

    @Test
    void release_allowsRetry() {
        svc.tryAcquire("k1");
        svc.release("k1");
        assertEquals(IdempotencyService.AcquireStatus.EXECUTE, svc.tryAcquire("k1").status());
    }

    @Test
    void blankKey_alwaysExecute() {
        assertEquals(IdempotencyService.AcquireStatus.EXECUTE, svc.tryAcquire(null).status());
        assertEquals(IdempotencyService.AcquireStatus.EXECUTE, svc.tryAcquire("  ").status());
        assertTrue(svc.peek("x").isEmpty());
    }
}
