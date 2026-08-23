package com.tepeu.os.orchestration;

import java.util.Objects;
import java.util.Optional;

public record CommandResult(
        boolean ok,
        CommandKind kind,
        String output,
        Optional<String> expansion) {

    public CommandResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(output, "output");
        expansion = expansion == null ? Optional.empty() : expansion;
    }

    public static CommandResult local(String output) {
        return new CommandResult(true, CommandKind.LOCAL, output == null ? "" : output, Optional.empty());
    }

    public static CommandResult prompt(String expansion) {
        Objects.requireNonNull(expansion, "expansion");
        if (expansion.isBlank()) {
            throw new IllegalArgumentException("prompt expansion blank");
        }
        return new CommandResult(true, CommandKind.PROMPT, "", Optional.of(expansion));
    }

    public static CommandResult failure(String message) {
        return new CommandResult(false, CommandKind.LOCAL, message == null ? "" : message, Optional.empty());
    }
}
