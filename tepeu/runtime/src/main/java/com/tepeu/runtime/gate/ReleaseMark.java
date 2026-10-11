package com.tepeu.runtime.gate;

import com.tepeu.runtime.word.ToolAsk;

import java.util.List;

/**
 * 这一次放行的结果，给一轮抄进过程记录。
 * 问模型成功时，旁边的工具从这里带走。不是步骤记录，也不交给判断。
 */
public record ReleaseMark(String name, String outcome, List<ToolAsk> asks) {

    /** 没有旁边的工具。 */
    public ReleaseMark(String name, String outcome) {
        this(name, outcome, List.of());
    }

    /** 空工具收成空名单。 */
    public ReleaseMark {
        asks = asks == null ? List.of() : List.copyOf(asks);
    }
}
