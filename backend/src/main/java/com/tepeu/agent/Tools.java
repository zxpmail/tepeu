package com.tepeu.agent;

import com.tepeu.agent.tool.DeleteFileTool;
import com.tepeu.agent.tool.ListDirTool;
import com.tepeu.agent.tool.ReadFileTool;
import com.tepeu.agent.tool.ReadOutputTool;
import com.tepeu.agent.tool.RunCommandTool;
import com.tepeu.agent.tool.RunSkillScriptTool;
import com.tepeu.agent.tool.SearchFileTool;
import com.tepeu.agent.tool.ToolRegistry;
import com.tepeu.agent.tool.WriteFileTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 本地工具显式清单 — 注册键与 {@code @Tool} 名一致。
 * MCP 工具不在此 register，由 {@link com.tepeu.agent.mcp.McpToolBridge} 并入 ChatService。
 */
@Configuration
public class Tools {

    @Bean
    public ToolRegistry toolRegistry(
            ListDirTool listDirTool,
            ReadFileTool readFileTool,
            WriteFileTool writeFileTool,
            DeleteFileTool deleteFileTool,
            SearchFileTool searchFileTool,
            RunCommandTool runCommandTool,
            ReadOutputTool readOutputTool,
            RunSkillScriptTool runSkillScriptTool) {
        ToolRegistry registry = new ToolRegistry();
        registry.register("list_files", listDirTool);
        registry.register("read_file", readFileTool);
        registry.register("write_file", writeFileTool);
        registry.register("delete_file", deleteFileTool);
        registry.register("search_files", searchFileTool);
        registry.register("run_command", runCommandTool);
        registry.register("read_output", readOutputTool);
        registry.register("run_skill_script", runSkillScriptTool);
        return registry;
    }
}
