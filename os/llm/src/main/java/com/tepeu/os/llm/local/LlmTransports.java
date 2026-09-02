package com.tepeu.os.llm.local;

import com.tepeu.os.llm.*;

import java.util.Optional;

/**
 * 调用方注入用。compose / SqliteAssembly <strong>不</strong>自动读环境变量。
 */
public final class LlmTransports {

    private LlmTransports() {
    }

    public static Optional<LlmTransport> fromEnv() {
        String anthropic = System.getenv("ANTHROPIC_API_KEY");
        if (anthropic != null && !anthropic.isBlank()) {
            return Optional.of(AnthropicHttpTransport.fromEnv());
        }
        String openai = System.getenv("OPENAI_API_KEY");
        if (openai != null && !openai.isBlank()) {
            return Optional.of(OpenAiHttpTransport.fromEnv());
        }
        return Optional.empty();
    }
}
