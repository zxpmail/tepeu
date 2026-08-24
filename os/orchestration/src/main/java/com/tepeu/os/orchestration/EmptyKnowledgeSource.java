package com.tepeu.os.orchestration;

import com.tepeu.os.identity.TurnContext;

import java.util.List;

/** 默认：无记忆平面，query 恒空。 */
public final class EmptyKnowledgeSource implements KnowledgeSource {

    @Override
    public List<Hit> query(TurnContext ctx, String queryText) {
        return List.of();
    }
}
