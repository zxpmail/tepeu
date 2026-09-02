package com.tepeu.os.host;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本机 CC Switch 当前 Claude 供应商。host 在 env / properties 都没有 key 时才读。
 * 认 {@code settings.json} 的 {@code currentProviderClaude}，不认错 app 的 {@code is_current}。
 */
record CcSwitchClaude(String id, String name, String apiKey, String baseUrl, String model) {

    private static final Pattern CURRENT_CLAUDE = Pattern.compile(
            "\"currentProviderClaude\"\\s*:\\s*\"([^\"]+)\"");

    static Optional<CcSwitchClaude> load() {
        return load(Path.of(System.getProperty("user.home"), ".cc-switch"));
    }

    static Optional<CcSwitchClaude> load(Path dir) {
        Objects.requireNonNull(dir, "dir");
        Path db = dir.resolve("cc-switch.db");
        if (!Files.isRegularFile(db)) {
            return Optional.empty();
        }
        Optional<String> id = currentClaudeId(dir.resolve("settings.json"));
        String url = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url)) {
            if (id.isPresent()) {
                Optional<CcSwitchClaude> byId = fetch(c, "id = ?", id.get());
                if (byId.isPresent()) {
                    return byId;
                }
            }
            return fetch(c, "app_type = 'claude' AND is_current = 1", null);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<String> currentClaudeId(Path settings) {
        if (!Files.isRegularFile(settings)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(settings, StandardCharsets.UTF_8);
            Matcher m = CURRENT_CLAUDE.matcher(text);
            if (m.find()) {
                return Optional.of(m.group(1));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<CcSwitchClaude> fetch(Connection c, String where, String id) throws Exception {
        String sql = """
                SELECT id, name,
                  coalesce(
                    json_extract(settings_config, '$.env.ANTHROPIC_AUTH_TOKEN'),
                    json_extract(settings_config, '$.env.ANTHROPIC_API_KEY'),
                    json_extract(settings_config, '$.env.OPENAI_API_KEY'),
                    json_extract(settings_config, '$.env.DEEPSEEK_API_KEY')
                  ) AS api_key,
                  coalesce(
                    json_extract(settings_config, '$.env.ANTHROPIC_BASE_URL'),
                    json_extract(settings_config, '$.env.OPENAI_BASE_URL'),
                    ''
                  ) AS base_url,
                  coalesce(
                    json_extract(settings_config, '$.env.ANTHROPIC_MODEL'),
                    json_extract(settings_config, '$.env.ANTHROPIC_DEFAULT_SONNET_MODEL'),
                    json_extract(settings_config, '$.env.OPENAI_MODEL'),
                    ''
                  ) AS model
                FROM providers WHERE %s LIMIT 1
                """.formatted(where);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (id != null) {
                ps.setString(1, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String key = blankToEmpty(rs.getString("api_key"));
                if (key.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new CcSwitchClaude(
                        blankToEmpty(rs.getString("id")),
                        blankToEmpty(rs.getString("name")),
                        key,
                        blankToEmpty(rs.getString("base_url")),
                        blankToEmpty(rs.getString("model"))));
            }
        }
    }

    private static String blankToEmpty(String s) {
        return s == null || s.isBlank() ? "" : s.strip();
    }
}
