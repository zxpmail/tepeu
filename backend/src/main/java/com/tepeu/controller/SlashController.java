package com.tepeu.controller;

import com.tepeu.agent.slash.SlashCommand;
import com.tepeu.agent.slash.SlashCommandRegistry;
import com.tepeu.agent.slash.SlashContext;
import com.tepeu.agent.slash.SlashResult;
import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.SlashExecuteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Slash 命令 API（DEV-PLAN Phase 14）。
 * {@code GET /api/slash/commands} 目录；{@code POST /api/slash} 执行（不调 LLM）。
 */
@RestController
@RequestMapping("/api/slash")
public class SlashController {

    private final SlashCommandRegistry registry;

    public SlashController(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/commands")
    public ResponseEntity<ApiResponse<?>> listCommands() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SlashCommand c : registry.list()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", c.name());
            m.put("description", c.description());
            m.put("usage", c.usage());
            m.put("requiresWorkspace", c.requiresWorkspace());
            items.add(m);
        }
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /** 命令执行错误统一由 GlobalExceptionHandler 映射（IllegalArgumentException → 400 VALIDATION_ERROR）。 */
    @PostMapping
    public ResponseEntity<ApiResponse<?>> execute(@RequestBody SlashExecuteRequest req) {
        ParsedLine parsed = parseCommandLine(req != null ? req.getCommand() : null);
        SlashContext ctx = new SlashContext(
                req != null ? req.getWorkspaceId() : null,
                req != null ? req.getSessionId() : null,
                parsed.args());
        SlashResult result = registry.execute(parsed.name(), ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("command", parsed.name());
        data.put("text", result.text());
        if (result.action() != null) {
            data.put("action", result.action());
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** 解析 {@code /help}、{@code help}、{@code /schedule list} */
    static ParsedLine parseCommandLine(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("command is required");
        }
        String line = raw.trim();
        if (line.startsWith("/")) {
            line = line.substring(1).trim();
        }
        if (line.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        String[] parts = line.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        List<String> args = parts.length > 1
                ? Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length))
                : List.of();
        return new ParsedLine(name, args);
    }

    record ParsedLine(String name, List<String> args) {}
}
