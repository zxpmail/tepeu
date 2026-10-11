package com.tepeu.runtime.judge;

import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;

/**
 * 判断做完没有。只看步骤记录里对得上的能力交回。
 * 问模型、读和查找的成功不能算做完。写、改或跑的成功才算。失败要对上完成标准那一句原文。
 */
public final class Judge {

    private final Ledger ledger;

    /** 接上同一本账。 */
    public Judge(Ledger ledger) {
        this.ledger = ledger;
    }

    /** 写下做完或没做完。不把结果交回调用的人。 */
    public void judge() {
        String goalId = null;
        String doneWhen = null;
        for (Step step : ledger.read()) {
            if (Kind.PIECE.equals(step.kind()) && step.writer() == Writer.base) {
                goalId = step.goalId();
                doneWhen = step.doneWhen();
            }
        }
        boolean made = false;
        for (Step step : ledger.read()) {
            if (supports(step, goalId, doneWhen)) {
                made = true;
            }
        }
        ledger.append(new Step(
                made ? Kind.DONE : Kind.NOT_DONE,
                Writer.judge,
                null,
                null,
                goalId,
                null,
                null,
                null,
                null,
                null));
    }

    /** 这一笔能力交回能不能撑起做完。正文里的编号不算。 */
    private static boolean supports(Step step, String goalId, String doneWhen) {
        if (!Kind.RETURN.equals(step.kind()) || step.writer() != Writer.gate) {
            return false;
        }
        if (goalId == null || !goalId.equals(step.goalId())) {
            return false;
        }
        boolean failure = step.resultKind() == ResultKind.失败
                || step.resultKind() == ResultKind.拒绝
                || step.resultKind() == ResultKind.超时;
        if (looks(step.capability())) {
            if (!failure) {
                return false;
            }
            return step.resultCode() != null && step.resultCode().equals(doneWhen);
        }
        if (!changes(step.capability())) {
            return false;
        }
        if (!failure) {
            return true;
        }
        return step.resultCode() != null && step.resultCode().equals(doneWhen);
    }

    /** 问模型、读、在文件里找、按名字找，成功都不算做完。 */
    private static boolean looks(String name) {
        return Ability.ASK.equals(name) || Ability.READ.equals(name)
                || Ability.GREP.equals(name) || Ability.GLOB.equals(name);
    }

    /** 写整篇、改一段、跑命令，成功才算做完。 */
    private static boolean changes(String name) {
        return Ability.WRITE.equals(name) || Ability.EDIT.equals(name) || Ability.RUN.equals(name);
    }
}
