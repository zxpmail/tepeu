package com.tepeu.os.session.local;

import com.tepeu.os.session.*;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 对着 ledger 加总 token，再和天花板比一下。
 * <p>
 * 只回答「还在不在预算里」，不喊停、不抛拒绝。Loop 听见 false 才不领下一句。
 * 没设顶 = 不限。顶是 0 = 一开就超。价钱空着的条目不加进去，免得假装花过钱。
 * 账本本身是真相；这里不写 slf4j、不打每一行用量。只在「已经超了」记一条运维诊断。
 */
public final class LedgerMetering implements Metering {

    private static final Logger LOG = System.getLogger(LedgerMetering.class.getName());
    private static final String COMPONENT = "session";
    private static final String CLASS_NAME = LedgerMetering.class.getSimpleName();

    private final OptionalLong tokenCeiling;

    private LedgerMetering(OptionalLong tokenCeiling) {
        this.tokenCeiling = Objects.requireNonNull(tokenCeiling, "tokenCeiling");
    }

    /** 不设顶。 */
    public static LedgerMetering unlimited() {
        return new LedgerMetering(OptionalLong.empty());
    }

    /**
     * 累计 token 必须严格小于这个数，才算还在预算里。
     * 等于天花板也算超。
     */
    public static LedgerMetering tokens(long ceiling) {
        if (ceiling < 0L) {
            throw new IllegalArgumentException("tokenCeiling < 0");
        }
        return new LedgerMetering(OptionalLong.of(ceiling));
    }

    public OptionalLong tokenCeiling() {
        return tokenCeiling;
    }

    /** 加总本会话 ledger 的 totalTokens，和顶比。不限顶直接 true。 */
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
        long ceiling = tokenCeiling.getAsLong();
        boolean within = spent < ceiling;
        if (!within) {
            LOG.log(Level.DEBUG,
                    "component={0} class={1} session={2} spent={3} ceiling={4} within=false",
                    COMPONENT, CLASS_NAME, session.id().value(), spent, ceiling);
        }
        return within;
    }
}
