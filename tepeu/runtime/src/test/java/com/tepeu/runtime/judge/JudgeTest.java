package com.tepeu.runtime.judge;

import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判断只认对得上的能力交回。
 * 模型说的那句话、执行登记，都不算。
 */
class JudgeTest {

    /** 完成标准整句等于 FAILED，失败就可以算做完。 */
    @Test
    void 失败代码对上算做完() {
        Ledger ledger = new Ledger();
        ledger.openPiece("做事", "这里", "FAILED");
        ledger.append(returned(Ability.READ, "g1", ResultKind.失败, "FAILED", ""));
        new Judge(ledger).judge();
        assertTrue(has(ledger, Kind.DONE));
        assertFalse(has(ledger, Kind.NOT_DONE));
    }

    /** 问模型成功不能单独算做完。失败代码对不上也不能。 */
    @Test
    void 问模型成功和普通失败都不算() {
        Ledger ask = new Ledger();
        ask.openPiece("做事", "这里", "看见");
        ask.append(returned(Ability.ASK, "g1", ResultKind.成功, null, "做完了"));
        new Judge(ask).judge();
        assertTrue(has(ask, Kind.NOT_DONE));
        assertFalse(has(ask, Kind.DONE));

        Ledger failed = new Ledger();
        failed.openPiece("做事", "这里", "看见");
        failed.append(returned(Ability.READ, "g1", ResultKind.失败, "FAILED", ""));
        new Judge(failed).judge();
        assertTrue(has(failed, Kind.NOT_DONE));
    }

    private static Step returned(String name, String goalId, ResultKind kind, String code, String body) {
        return new Step(Kind.RETURN, Writer.gate, null, name, goalId, kind, code, body, null, null);
    }

    private static boolean has(Ledger ledger, String kind) {
        return ledger.read().stream().anyMatch(step -> kind.equals(step.kind()) && step.writer() == Writer.judge);
    }
}
