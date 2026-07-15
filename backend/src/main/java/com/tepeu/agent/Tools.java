package com.tepeu.agent;

import com.tepeu.agent.tool.FileTools;
import com.tepeu.agent.tool.ShellTools;
import com.tepeu.agent.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 可调用工具的唯一显式清单。
 * 新增工具：实现类 + 在本文件 register 一行。不必改 ChatService。
 */
@Configuration
public class Tools {

    @Bean
    public ToolRegistry toolRegistry(FileTools fileTools, ShellTools shellTools) {
        ToolRegistry registry = new ToolRegistry();
        // 显式注册，而非让 ChatService 硬编码依赖每个工具类型
        registry.register("fileTools", fileTools);
        registry.register("shellTools", shellTools);
        return registry;
    }
}
