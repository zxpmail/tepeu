package com.tepeu.os.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * 运行主体 — 谁在动（id）+ 种类（kind）+ 可选展示名。
 * 展示名非鉴权真相；策略与计量键用 {@link #id()}。
 */
public record Principal(PrincipalId id, AgentKind kind, Optional<String> displayName) {
    public Principal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        displayName = displayName == null ? Optional.empty() : displayName;
    }

    /** 本机默认：PERSONAL、无展示名。 */
    public static Principal personal(PrincipalId id) {
        return new Principal(id, AgentKind.PERSONAL, Optional.empty());
    }
}
