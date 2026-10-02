package com.tepeu.runtime.ledger;

import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.Writer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 账只记人和任务编号，并挡住同时写入的第二笔。
 * 不检查字段，也不替检查口写拒绝。
 */
class LedgerTest {

    /** 装好就有「谁」，没有登录名。 */
    @Test
    void 装好记下这一次使用的人() {
        Ledger ledger = new Ledger();
        assertTrue(ledger.read().stream().anyMatch(step ->
                Kind.WHO.equals(step.kind())
                        && step.writer() == Writer.base
                        && "这一次使用的人".equals(step.body())));
    }

    /** 三个字段的原文留在这次任务上，编号由账写。 */
    @Test
    void 记下任务留下三句原文() {
        Ledger ledger = new Ledger();
        String goalId = ledger.openPiece("去写文件", "只许这个目录", "文件里有这句");
        assertEquals("g1", goalId);
        assertTrue(ledger.read().stream().anyMatch(step ->
                Kind.PIECE.equals(step.kind())
                        && step.writer() == Writer.base
                        && goalId.equals(step.goalId())
                        && "去写文件".equals(step.body())
                        && "文件里有这句".equals(step.doneWhen())
                        && "只许这个目录".equals(step.limit())));
    }

    /** 账不检查字段，空话也会记下。合不合格是别人的事。 */
    @Test
    void 不检查字段合不合格() {
        Ledger ledger = new Ledger();
        String goalId = ledger.openPiece("按上面说的做", " ", "完成");
        assertEquals("g1", goalId);
        assertFalse(ledger.read().stream().anyMatch(step -> Refusal.GATE_BLOCKED.equals(step.refusalCode())));
    }

    /** 两笔同时追加，第二笔记不下来。 */
    @Test
    void 同时写入第二笔记不下来() {
        Ledger ledger = new Ledger();
        int before = ledger.read().size();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                ledger.append(new Step(Kind.STOP, Writer.gate, null, null, null, null, null, null, null, null));
            } catch (Throwable thrown) {
                error.set(thrown);
            }
        });
        ledger.runLocked(() -> {
            writer.start();
            try {
                writer.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待另一笔写入时被打断", ex);
            }
        });
        assertInstanceOf(IllegalStateException.class, error.get());
        assertEquals(before, ledger.read().size());
        assertFalse(ledger.read().stream().anyMatch(step -> Kind.STOP.equals(step.kind())));
    }
}
