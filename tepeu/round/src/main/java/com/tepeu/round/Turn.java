package com.tepeu.round;

import java.util.List;

/**
 * 跑一轮的正式入口。
 * 装没装、费用在不在、能力交回的结果都不在这里。
 */
public record Turn(
        Entry entry,
        boolean pureChat,
        boolean stopHit,
        boolean askModel,
        Field goal,
        Field limit,
        Field doneWhen,
        List<String> calls,
        String modelUtterance
) {

    /** 补上缺省：主轮、未标明纯聊、不停、不问模型、空名单。 */
    public Turn {
        if (entry == null) {
            entry = Entry.main;
        }
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
