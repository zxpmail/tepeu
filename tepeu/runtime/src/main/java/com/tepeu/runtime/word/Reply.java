package com.tepeu.runtime.word;

import java.util.List;

/**
 * 能力交回给检查口的结果。做事不写步骤记录，只交回这一笔。
 * 问模型可以在正文旁边带上工具。正文本身不带工具。
 */
public record Reply(ResultKind kind, String resultCode, String body, List<ToolAsk> asks) {

    /** 没有旁边的工具。 */
    public Reply(ResultKind kind, String resultCode, String body) {
        this(kind, resultCode, body, List.of());
    }

    /** 空正文、空工具都收成空。 */
    public Reply {
        body = body == null ? "" : body;
        asks = asks == null ? List.of() : List.copyOf(asks);
    }

    /** 成功，正文按交来的字留下。 */
    public static Reply success(String body) {
        return new Reply(ResultKind.成功, null, body);
    }

    /** 成功，正文给人看，工具在旁边。 */
    public static Reply success(String body, List<ToolAsk> asks) {
        return new Reply(ResultKind.成功, null, body, asks);
    }
}
