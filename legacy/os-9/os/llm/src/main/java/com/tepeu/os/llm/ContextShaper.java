package com.tepeu.os.llm;

import java.util.List;

/** 观测面 shape/redact。不改 entries。 */
@FunctionalInterface
public interface ContextShaper {

    List<CanonicalTurn> shape(List<CanonicalTurn> normalized);
}
