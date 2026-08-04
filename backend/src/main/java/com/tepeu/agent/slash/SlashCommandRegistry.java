package com.tepeu.agent.slash;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 收集全部 {@link SlashCommand} Bean，按名查找与列举。
 * 关联：SlashController、各 commands/*。
 */
@Component
public class SlashCommandRegistry {

    private final Map<String, SlashCommand> byName = new LinkedHashMap<>();

    public SlashCommandRegistry(List<SlashCommand> commands) {
        List<SlashCommand> sorted = new ArrayList<>(commands);
        sorted.sort(Comparator.comparing(SlashCommand::name));
        for (SlashCommand c : sorted) {
            String key = c.name().toLowerCase(Locale.ROOT);
            if (byName.containsKey(key)) {
                throw new IllegalStateException("重复的 Slash 命令名: " + key);
            }
            byName.put(key, c);
        }
    }

    public Optional<SlashCommand> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT).trim()));
    }

    public List<SlashCommand> list() {
        return List.copyOf(byName.values());
    }

    /** 执行：未知命令抛 IllegalArgumentException。 */
    public SlashResult execute(String name, SlashContext ctx) {
        SlashCommand cmd = find(name)
                .orElseThrow(() -> new IllegalArgumentException("未知命令：/" + name));
        if (cmd.requiresWorkspace()
                && (ctx.workspaceId() == null || ctx.workspaceId().isBlank())) {
            throw new IllegalArgumentException("命令 /" + name + " 需要先选择工作区");
        }
        return cmd.execute(ctx);
    }
}
