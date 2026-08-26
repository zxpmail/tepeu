package com.tepeu.os.llm;

import com.tepeu.os.observation.CanonicalTurn;

import java.util.List;
import java.util.Objects;

/** prepare 之后、transport 之前的可观测请求（断言落点）。 */
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
