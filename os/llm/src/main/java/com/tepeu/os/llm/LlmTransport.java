package com.tepeu.os.llm;

import com.tepeu.os.llm.local.OpenAiProjector;
import com.tepeu.os.llm.local.AnthropicProjector;
import com.tepeu.os.llm.local.CanonicalJson;
import com.tepeu.os.observation.CanonicalTurn;
import com.tepeu.os.observation.local.LogDeriver;
import com.tepeu.os.observation.Observation;
import com.tepeu.os.observation.local.SharedNormalizer;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.syscall.Usage;

import java.util.List;

/** 传输缝。fake 与 Anthropic / OpenAI HTTP 薄壳。 */
public interface LlmTransport {

    record Reply(String output, Usage usage) {
    }

    Reply complete(PreparedRequest prepared);

    static PreparedRequest prepare(
            List<SessionEvent> surface,
            ProtocolFamily family,
            String model,
            String system) {
        return prepare(surface, family, model, system, AnthropicProjector.DEFAULT_MAX_TOKENS);
    }

    static PreparedRequest prepare(
            List<SessionEvent> surface,
            ProtocolFamily family,
            String model,
            String system,
            int maxTokens) {
        List<CanonicalTurn> normalized = Observation.view(surface);
        MapWire wire = project(family, model, system == null ? "" : system, normalized, maxTokens);
        String json = CanonicalJson.write(wire.root());
        long throughSeq = surface.isEmpty() ? 0L : surface.get(surface.size() - 1).seq();
        return new PreparedRequest(
                family,
                model,
                json,
                CanonicalJson.sha256Hex(json),
                normalized,
                LogDeriver.VERSION,
                SharedNormalizer.VERSION,
                wire.projectVersion(),
                throughSeq);
    }

    private static MapWire project(
            ProtocolFamily family,
            String model,
            String system,
            List<CanonicalTurn> normalized,
            int maxTokens) {
        return switch (family) {
            case ANTHROPIC -> new MapWire(
                    AnthropicProjector.project(model, system, normalized, maxTokens),
                    AnthropicProjector.VERSION);
            case OPENAI -> new MapWire(OpenAiProjector.project(model, system, normalized),
                    OpenAiProjector.VERSION);
        };
    }

    record MapWire(java.util.Map<String, Object> root, String projectVersion) {
    }
}
