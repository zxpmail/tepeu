package com.tepeu.os.llm;

import java.util.List;
import java.util.Objects;

/** prepare ä¹åãtransport ä¹åçå¯è§æµè¯·æ±ï¼æ­è¨è½ç¹ï¼ã */
public record PreparedRequest(
        ProtocolFamily family,
        String model,
        String wireJson,
        String digest,
        List<CanonicalTurn> normalized,
        String deriveVersion,
        String normalizeVersion,
        String projectVersion,
        long throughSeq) {

    public PreparedRequest {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(wireJson, "wireJson");
        Objects.requireNonNull(digest, "digest");
        normalized = List.copyOf(Objects.requireNonNull(normalized, "normalized"));
        Objects.requireNonNull(deriveVersion, "deriveVersion");
        Objects.requireNonNull(normalizeVersion, "normalizeVersion");
        Objects.requireNonNull(projectVersion, "projectVersion");
    }
}
