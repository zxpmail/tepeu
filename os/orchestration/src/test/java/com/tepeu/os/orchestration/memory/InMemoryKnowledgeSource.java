package com.tepeu.os.orchestration.memory;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.orchestration.KnowledgeSource;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 测试/演示用固定命中表 — 不是向量检索。 */
public final class InMemoryKnowledgeSource implements KnowledgeSource {

    private final List<Hit> hits;

    public InMemoryKnowledgeSource(List<Hit> hits) {
        this.hits = List.copyOf(Objects.requireNonNull(hits, "hits"));
    }

    @Override
    public List<Hit> query(TurnContext ctx, String queryText) {
        Objects.requireNonNull(ctx, "ctx");
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        String q = queryText.toLowerCase(Locale.ROOT);
        return hits.stream()
                .filter(h -> h.snippet().toLowerCase(Locale.ROOT).contains(q)
                        || h.sourceId().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}
