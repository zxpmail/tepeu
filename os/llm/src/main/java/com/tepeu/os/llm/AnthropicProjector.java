package com.tepeu.os.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anthropic Messages 族投影。v1：单 cache 断点挂在最后一条 message（若有）。 */
public final class AnthropicProjector {

    public static final String VERSION = "1";

    private AnthropicProjector() {
    }

    public static Map<String, Object> project(String model, String system, List<CanonicalTurn> normalized) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", model);
        if (system != null && !system.isBlank()) {
            root.put("system", system);
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            CanonicalTurn turn = normalized.get(i);
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", role(turn));
            msg.put("content", turn.body());
            if (i == normalized.size() - 1) {
                msg.put("cache_control", Map.of("type", "ephemeral"));
            }
            messages.add(msg);
        }
        root.put("messages", messages);
        return root;
    }

    private static String role(CanonicalTurn turn) {
        return switch (turn.role()) {
            case USER, TOOL -> "user";
            case ASSISTANT, REASONING -> "assistant";
        };
    }
}
