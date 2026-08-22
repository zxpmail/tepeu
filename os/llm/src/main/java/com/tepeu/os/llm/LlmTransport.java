package com.tepeu.os.llm;

import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.syscall.Usage;

import java.util.List;

/** 传输缝。fake 先行；真 HTTP 一族一刀后续裁。 */
public interface LlmTransport {

    record Reply(String output, Usage usage) {
    }

    Reply complete(PreparedRequest prepared);

    static PreparedRequest prepare(
            List<SessionEvent> surface,
            ProtocolFamily family,
            String model,
            String system) {
        List<CanonicalTurn> derived = LogDeriver.derive(surface);
        List<CanonicalTurn> normalized = SharedNormalizer.normalize(derived);
        MapWire wire = project(family, model, system == null ? "" : system, normalized);
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
            ProtocolFamily family, String model, String system, List<CanonicalTurn> normalized) {
        return switch (family) {
            case ANTHROPIC -> new MapWire(AnthropicProjector.project(model, system, normalized),
                    AnthropicProjector.VERSION);
            case OPENAI -> new MapWire(OpenAiProjector.project(model, system, normalized),
                    OpenAiProjector.VERSION);
        };
    }

    record MapWire(java.util.Map<String, Object> root, String projectVersion) {
    }
}
