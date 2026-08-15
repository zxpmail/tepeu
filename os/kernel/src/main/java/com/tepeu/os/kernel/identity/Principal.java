package com.tepeu.os.kernel.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * 运行主体：谁在动 + 可选 agentKind。
 */
public record Principal(PrincipalId id, AgentKind kind, Optional<String> displayName) {
    public Principal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        displayName = displayName == null ? Optional.empty() : displayName;
    }

    public static Principal personal(PrincipalId id) {
        return new Principal(id, AgentKind.PERSONAL, Optional.empty());
    }
}
