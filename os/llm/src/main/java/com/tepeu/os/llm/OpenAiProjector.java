package com.tepeu.os.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions 族投影。v1：无 cache_control。 */
public final class OpenAiProjector {

    public static final String VERSION = "1";

    private OpenAiProjector() {
    }

    public static Map<String, Object> project(String model, String system, List<CanonicalTurn> normalized) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", model);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (system != null && !system.isBlank()) {
            messages.add(Map.of("role", "system", "content", system));
        }
        for (CanonicalTurn turn : normalized) {
            messages.add(Map.of("role", role(turn), "content", turn.body()));
        }
        root.put("messages", messages);
        return root;
    }

    private static String role(CanonicalTurn turn) {
        return switch (turn.role()) {
            case USER -> "user";
            case ASSISTANT, REASONING -> "assistant";
            case TOOL -> "tool";
        };
    }
}
