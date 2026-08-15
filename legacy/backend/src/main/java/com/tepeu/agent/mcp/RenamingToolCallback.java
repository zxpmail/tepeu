package com.tepeu.agent.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 为 MCP 工具名加 {@code mcp_} 前缀，便于 Hook 统一识别外部工具。
 */
final class RenamingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String prefixedName;

    RenamingToolCallback(ToolCallback delegate, String prefixedName) {
        this.delegate = delegate;
        this.prefixedName = prefixedName;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        ToolDefinition d = delegate.getToolDefinition();
        return new ToolDefinition() {
            @Override
            public String name() {
                return prefixedName;
            }

            @Override
            public String description() {
                return d.description();
            }

            @Override
            public String inputSchema() {
                return d.inputSchema();
            }
        };
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }
}
