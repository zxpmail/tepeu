package com.tepeu.os.orchestration.local;

import com.tepeu.os.orchestration.*;

import com.tepeu.os.identity.TurnContext;

import java.util.List;
import java.util.Objects;

/** /help — 列出已注册命令。local，零 token。 */
public final class HelpCommand implements CommandHandler {

    private final CommandDispatcher dispatcher;

    public HelpCommand(CommandDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public CommandKind kind() {
        return CommandKind.LOCAL;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "list commands";
    }

    @Override
    public CommandResult execute(TurnContext ctx, List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String name : dispatcher.registered()) {
            dispatcher.handler(name).ifPresent(handler -> {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append('/').append(name).append(' ').append(handler.description());
            });
        }
        return CommandResult.local(sb.toString());
    }
}
