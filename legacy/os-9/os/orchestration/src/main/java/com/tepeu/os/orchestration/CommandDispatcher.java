package com.tepeu.os.orchestration;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

/**
 * Slash 必经此缝：不经模型、不进 PromptAssembly。
 * prompt 型展开后 {@code inbox.enqueue}（不旁路拼消息）。
 */
public final class CommandDispatcher {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

    public record Parsed(String name, List<String> args) {
        public Parsed {
            Objects.requireNonNull(name, "name");
            args = args == null ? List.of() : List.copyOf(args);
        }
    }

    private final ConcurrentSkipListMap<String, CommandHandler> handlers = new ConcurrentSkipListMap<>();

    public void register(CommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String name = handler.name();
        Objects.requireNonNull(name, "name");
        String key = name.toLowerCase(Locale.ROOT);
        if (!NAME.matcher(key).matches()) {
            throw new IllegalArgumentException("command name: " + name);
        }
        if (handlers.putIfAbsent(key, handler) != null) {
            throw new IllegalStateException("command already registered: " + key);
        }
    }

    public List<String> registered() {
        return List.copyOf(handlers.keySet());
    }

    public Optional<CommandHandler> handler(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlers.get(name.toLowerCase(Locale.ROOT)));
    }

    public static Optional<Parsed> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String text = line.strip();
        if (!text.startsWith("/")) {
            return Optional.empty();
        }
        String rest = text.substring(1).strip();
        if (rest.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = rest.split("\\s+");
        String name = parts[0].toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }
        return Optional.of(new Parsed(name, args));
    }

    public CommandResult dispatch(TurnContext ctx, Session session, String line) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(session, "session");
        Optional<Parsed> parsed = parse(line);
        if (parsed.isEmpty()) {
            return CommandResult.failure("not a command");
        }
        Parsed p = parsed.get();
        CommandHandler handler = handlers.get(p.name());
        if (handler == null) {
            return CommandResult.failure("unknown command: " + p.name());
        }
        CommandResult result = handler.execute(ctx, p.args());
        if (result == null) {
            return CommandResult.failure("handler returned null");
        }
        if (result.ok() && result.kind() == CommandKind.PROMPT) {
            String expansion = result.expansion().orElse("");
            if (expansion.isBlank()) {
                return CommandResult.failure("prompt expansion blank");
            }
            session.inbox().enqueue(expansion, Optional.of("command:" + p.name()));
        }
        return result;
    }
}
