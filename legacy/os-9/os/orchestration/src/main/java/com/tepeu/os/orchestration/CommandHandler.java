package com.tepeu.os.orchestration;

import com.tepeu.os.identity.TurnContext;

import java.util.List;

/** Slash 处理器。local 不经模型；prompt 展开后进 Inbox。 */
public interface CommandHandler {
    CommandKind kind();

    String name();

    String description();

    CommandResult execute(TurnContext ctx, List<String> args);
}
