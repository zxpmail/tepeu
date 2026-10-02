package com.tepeu.runtime.word;

/**
 * 能力交回给检查口的结果。做事不写步骤记录，只交回这一笔。
 */
public record Reply(ResultKind kind, String resultCode, String body) {

    /** 成功，正文按交来的字留下。 */
    public static Reply success(String body) {
        return new Reply(ResultKind.成功, null, body == null ? "" : body);
    }

    /** 正文。空引用当成空字。 */
    public String body() {
        return body == null ? "" : body;
    }
}
