package com.tepeu.os.llm.local;

import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.PreparedRequest;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.llm.CanonicalTurn;
import com.tepeu.os.llm.Observation;
import com.tepeu.os.session.SessionEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本机传输工厂与准备管道。compose / SqliteAssembly 不自动读环境变量。
 */
public final class LlmTransports {

    private LlmTransports() {
    }

    public static PreparedRequest prepare(
            List<SessionEvent> surface,
            ProtocolFamily family,
            String model,
            String system) {
        return prepare(surface, family, model, system, AnthropicProjector.DEFAULT_MAX_TOKENS);
    }

    public static PreparedRequest prepare(
            List<SessionEvent> surface,
            ProtocolFamily family,
            String model,
            String system,
            int maxTokens) {
        List<CanonicalTurn> normalized = Observation.view(surface);
        String systemText = system == null ? "" : system;
        Map<String, Object> root;
        String projectVersion;
        if (family == ProtocolFamily.ANTHROPIC) {
            root = AnthropicProjector.project(model, systemText, normalized, maxTokens);
            projectVersion = AnthropicProjector.VERSION;
        } else if (family == ProtocolFamily.OPENAI) {
            root = OpenAiProjector.project(model, systemText, normalized);
            projectVersion = OpenAiProjector.VERSION;
        } else {
            throw new IllegalArgumentException("family");
        }
        String json = CanonicalJson.write(root);
        long throughSeq = surface.isEmpty() ? 0L : surface.get(surface.size() - 1).seq();
        return new PreparedRequest(
                family,
                model,
                json,
                CanonicalJson.sha256Hex(json),
                normalized,
                LogDeriver.VERSION,
                SharedNormalizer.VERSION,
                projectVersion,
                throughSeq);
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
