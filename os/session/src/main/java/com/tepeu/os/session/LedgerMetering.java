package com.tepeu.os.session;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * 从会话 ledger 派生消耗，和配置的 token 硬顶比较。
 * {@code withinBudget} 只供数，不抛裁决（ADR-016 第七轮）。
 * 无顶 = 不限；顶 0 = 零预算（始终超限）。cost 空不参与（禁止假装有价）。
 */
public final class LedgerMetering implements Metering {

    private final OptionalLong tokenCeiling;

    private LedgerMetering(OptionalLong tokenCeiling) {
        this.tokenCeiling = Objects.requireNonNull(tokenCeiling, "tokenCeiling");
    }

    public static LedgerMetering unlimited() {
        return new LedgerMetering(OptionalLong.empty());
    }

    /** 累计 {@code usage.totalTokens()} 须严格小于 ceiling 才算在预算内。 */
    public static LedgerMetering tokens(long ceiling) {
        if (ceiling < 0L) {
            throw new IllegalArgumentException("tokenCeiling < 0");
        }
        return new LedgerMetering(OptionalLong.of(ceiling));
    }

    public OptionalLong tokenCeiling() {
        return tokenCeiling;
    }

    @Override
    public boolean withinBudget(Session session) {
        Objects.requireNonNull(session, "session");
        if (tokenCeiling.isEmpty()) {
            return true;
        }
        long spent = 0L;
        for (LedgerEntry entry : session.ledger().readAll()) {
            spent += entry.usage().totalTokens();
        }
        return spent < tokenCeiling.getAsLong();
    }
}
