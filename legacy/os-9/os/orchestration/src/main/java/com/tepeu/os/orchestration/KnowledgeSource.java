package com.tepeu.os.orchestration;

import com.tepeu.os.identity.TurnContext;

import java.util.List;
import java.util.Objects;

/**
 * 记忆平面内容源（⑤/② 支撑）— 知识→Section 唯一入口；无命中则不得捏造 {@code memory_hits}。
 */
public interface KnowledgeSource {

    record Hit(String sourceId, String snippet) {
        public Hit {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(snippet, "snippet");
            if (sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId blank");
            }
        }
    }

    List<Hit> query(TurnContext ctx, String queryText);
}
