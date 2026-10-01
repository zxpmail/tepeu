package com.tepeu.identity;

import java.util.Objects;

/** 运行主体。 */
public record Principal(PrincipalId id) {
    public Principal {
        Objects.requireNonNull(id, "id");
    }
}
