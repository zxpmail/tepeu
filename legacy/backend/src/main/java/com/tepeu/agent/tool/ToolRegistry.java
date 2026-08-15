package com.tepeu.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具显式注册表。新增工具时在 {@code com.tepeu.agent.Tools} 中 register，
 * 不依赖组件扫描发现工具列表。关联：Tools、ChatService。
 */
public final class ToolRegistry {

    private final Map<String, Object> tools = new LinkedHashMap<>();

    /** 按名称登记一个带 @Tool 方法的工具 bean。 */
    public ToolRegistry register(String name, Object toolBean) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name required");
        }
        if (toolBean == null) {
            throw new IllegalArgumentException("tool bean required");
        }
        tools.put(name, toolBean);
        return this;
    }

    /** 供 ToolCallbacks.from(...) 展开。 */
    public Object[] beans() {
        return tools.values().toArray();
    }

    /** 已注册名称（调试 / Agent 阅读用）。 */
    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(tools.keySet()));
    }
}
