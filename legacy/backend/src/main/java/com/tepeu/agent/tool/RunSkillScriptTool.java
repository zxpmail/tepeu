package com.tepeu.agent.tool;

import com.tepeu.runtime.ScriptSandbox;
import com.tepeu.runtime.WorkspaceScriptFs;
import com.tepeu.service.WorkspacePathResolver;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 工具 — 在 GraalJS 沙箱中运行技能脚本（workspace 内约定路径 + 内置 demo）。
 * 工具名 {@code run_skill_script}。关联：ScriptSandbox、ADR-015。
 */
@Component
public class RunSkillScriptTool extends WorkspaceBoundTool {

    public static final String DEMO_NAME = "demo";
    private static final String DEMO_CLASSPATH = "runtime/demo-skill.js";

    private final ScriptSandbox sandbox;

    @Autowired
    public RunSkillScriptTool(WorkspacePathResolver pathResolver, ScriptSandbox sandbox) {
        super(pathResolver);
        this.sandbox = sandbox;
    }

    /** 测试缝 */
    RunSkillScriptTool(Path basePath, ScriptSandbox sandbox) {
        super(basePath);
        this.sandbox = sandbox;
    }

    public static RunSkillScriptTool forTests(Path basePath, ScriptSandbox sandbox) {
        return new RunSkillScriptTool(basePath, sandbox);
    }

    @Tool(name = "run_skill_script", description =
            "Run a sandboxed JavaScript skill script. "
            + "Use script='demo' for the built-in echo demo, or a workspace-relative path under /scripts/ ending with .js. "
            + "The script may only read/write files inside the current workspace via fs.readText/writeText. "
            + "Optional input is exposed as the global string `input`.")
    public String runSkillScript(
            @ToolParam(description = "Built-in name \"demo\", or workspace path like /scripts/my.js")
            String script,
            @ToolParam(description = "Optional JSON/text passed to the script as global `input`.")
            String input) {
        if (script == null || script.isBlank()) {
            return "ERROR: script is required (use \"demo\" or /scripts/*.js)";
        }
        Path base = currentBasePath();
        try {
            String source = loadSource(base, script.trim());
            String out = sandbox.eval(base, source, input, null);
            return out != null ? out : "ok";
        } catch (ScriptSandbox.ScriptTimeoutException e) {
            return "ERROR: " + e.getMessage();
        } catch (ScriptSandbox.ScriptSandboxException e) {
            return "ERROR: " + e.getMessage();
        } catch (IOException e) {
            return "ERROR: cannot load script: " + e.getMessage();
        } catch (SecurityException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String loadSource(Path workspaceRoot, String script) throws IOException {
        if (DEMO_NAME.equalsIgnoreCase(script) || "demo-skill".equalsIgnoreCase(script)) {
            ClassPathResource res = new ClassPathResource(DEMO_CLASSPATH);
            if (!res.exists()) {
                throw new IOException("demo script missing on classpath");
            }
            try (InputStream in = res.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String norm = WorkspacePathResolver.normalizeRelPath(script);
        if (!norm.startsWith("/scripts/") || !norm.toLowerCase().endsWith(".js")) {
            throw new SecurityException(
                    "only \"demo\" or workspace paths under /scripts/*.js are allowed");
        }
        Path file = WorkspacePathResolver.resolveSafely(workspaceRoot, norm);
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("script not found: " + norm);
        }
        long size = Files.size(file);
        if (size > WorkspaceScriptFs.MAX_BYTES) {
            throw new IOException("script too large");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
