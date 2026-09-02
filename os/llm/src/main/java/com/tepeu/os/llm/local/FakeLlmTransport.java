package com.tepeu.os.llm.local;

import com.tepeu.os.llm.*;

import com.tepeu.os.syscall.Usage;

/** 离线假传输 — 不发 HTTP，返回固定文本。 */
public final class FakeLlmTransport implements LlmTransport {

    public static final String OUTPUT = "fake-ok";

    @Override
    public Reply complete(PreparedRequest prepared) {
        return new Reply(OUTPUT, new Usage(1, 1, 0, 0));
    }
}
