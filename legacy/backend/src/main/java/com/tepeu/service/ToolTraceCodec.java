package com.tepeu.service;

import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将工具 SSE 事件编码为可持久化的 system 消息（兼容现有 role CHECK）。
 * 前端 loadSession 解码为 tool 卡片；Orchestrator 跳过 system，不进 LLM 历史。
 */
public final class ToolTraceCodec {

    public static final String PREFIX = "TEPEU_TOOL_V1:";

    private ToolTraceCodec() {}

    /** 编码 tool_call / tool_result 为 system.content；无法编码则返回 null */
    public static String encode(Map<String, Object> event, ObjectMapper mapper) {
        if (event == null || mapper == null) return null;
        Object type = event.get("type");
        if (!"tool_call".equals(type) && !"tool_result".equals(type)) {
            return null;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tool", event.get("tool"));
            payload.put("toolKind", event.get("toolKind"));
            if ("tool_call".equals(type)) {
                payload.put("toolState", "call");
                Object params = event.get("params");
                payload.put("content", params == null ? "{}" : mapper.writeValueAsString(params));
            } else {
                payload.put("toolState", "result");
                Object content = event.get("content");
                payload.put("content", content == null ? "" : content.toString());
            }
            return PREFIX + mapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
