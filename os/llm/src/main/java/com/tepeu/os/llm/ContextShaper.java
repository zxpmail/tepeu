package com.tepeu.os.llm;

import java.util.List;

/**
 * ModelContext 管道第三段 — 观测面 shape/redact（不改 entries 真相；verifier 仍读全量 surface）。
 */
@FunctionalInterface
public interface ContextShaper {

    List<CanonicalTurn> shape(List<CanonicalTurn> normalized);
}
