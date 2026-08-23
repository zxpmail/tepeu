package com.tepeu.os.orchestration;

import java.util.Objects;

/** 超预算出账单的一行。 */
public record Omission(String sectionId, String reason) {
    public Omission {
        Objects.requireNonNull(sectionId, "sectionId");
        Objects.requireNonNull(reason, "reason");
    }
}
