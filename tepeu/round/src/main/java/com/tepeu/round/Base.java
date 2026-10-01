package com.tepeu.round;

import java.util.Objects;

/**
 * 薄底：记下这一件，读步骤账。不查脏单，不写拒绝和做成。
 */
public final class Base {

    private final Ledger ledger;
    private int sequence;

    /** 接上这一本账。 */
    Base(Ledger ledger) {
        this.ledger = ledger;
    }

    /** 开跑时分配编号，把目标、限制和完成标准记在这一件上。 */
    public String openPiece(String goal, String limit, String doneWhen) {
        Objects.requireNonNull(limit, "限制不能是空引用");
        sequence = sequence + 1;
        String goalId = "g" + sequence;
        ledger.append(new LedgerEntry(
                LedgerEntry.PIECE,
                Writer.base,
                null,
                null,
                goalId,
                null,
                null,
                goal,
                doneWhen,
                limit));
        return goalId;
    }

    /** 读回步骤账。 */
    public java.util.List<LedgerEntry> readLedger() {
        return ledger.read();
    }
}
