package com.tepeu.runtime.round;

import com.tepeu.runtime.word.Entry;
import com.tepeu.runtime.word.Field;

import java.util.List;

/**
 * 外面交进的这一次输入。没有运行。
 * 没写的那些，按对外那一篇的缺省补上。
 */
public record Turn(
        Entry entry,
        boolean pureChat,
        boolean stopHit,
        String said,
        boolean askModel,
        Field goal,
        Field limit,
        Field doneWhen,
        List<Call> calls,
        String modelUtterance
) {

    /** 补上缺省。正题、不只聊天、不停、不问模型、空名单。 */
    public Turn {
        if (entry == null) {
            entry = Entry.main;
        }
        said = said == null ? "" : said;
        if (goal == null) {
            goal = Field.missing();
        }
        if (limit == null) {
            limit = Field.missing();
        }
        if (doneWhen == null) {
            doneWhen = Field.missing();
        }
        calls = calls == null ? List.of() : List.copyOf(calls);
        modelUtterance = modelUtterance == null ? "" : modelUtterance;
    }
}
