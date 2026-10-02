package com.tepeu.runtime.ledger;

import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;

/**
 * 步骤记录上的一笔。
 * 账保管它，检查口和判断来写自己的那一格。
 */
public record Step(
        String kind,
        Writer writer,
        String refusalCode,
        String capability,
        String goalId,
        ResultKind resultKind,
        String resultCode,
        String body,
        String doneWhen,
        String limit
) {
}
