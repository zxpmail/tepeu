package com.tepeu.os.llm;

import com.tepeu.os.syscall.Usage;

/** 传输缝。fake 与 Anthropic / OpenAI HTTP 薄壳。准备管道在 {@code llm.local}。 */
public interface LlmTransport {

    record Reply(String output, Usage usage) {
    }

    Reply complete(PreparedRequest prepared);
}
