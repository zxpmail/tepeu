package com.tepeu.round;

/**
 * 判定做成。自己读步骤账，不读终答，不把结果交回跑一轮。
 */
public final class Judge {

    private final Ledger ledger;

    /** 接上同一本账。不调用薄底的记下这一件。 */
    Judge(Ledger ledger) {
        this.ledger = ledger;
    }

    /** 写成成或未做成。问模型的成功返回不算。 */
    public void judge() {
        String goalId = null;
        String doneWhen = null;
        for (LedgerEntry entry : ledger.read()) {
            if (LedgerEntry.PIECE.equals(entry.kind()) && entry.writer() == Writer.base) {
                goalId = entry.goalId();
                doneWhen = entry.doneWhen();
            }
        }
        boolean made = false;
        for (LedgerEntry entry : ledger.read()) {
            if (supports(entry, goalId, doneWhen)) {
                made = true;
            }
        }
        ledger.append(new LedgerEntry(
                made ? LedgerEntry.DONE : LedgerEntry.NOT_DONE,
                Writer.judge,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    /** 这一笔平台返回能不能撑起做成。正文里的编号不算。 */
    private static boolean supports(LedgerEntry entry, String goalId, String doneWhen) {
        if (!LedgerEntry.RETURN.equals(entry.kind()) || entry.writer() != Writer.gate) {
            return false;
        }
        if (goalId == null || !goalId.equals(entry.goalId())) {
            return false;
        }
        boolean failure = entry.resultKind() == ResultKind.失败
                || entry.resultKind() == ResultKind.拒绝
                || entry.resultKind() == ResultKind.超时;
        if (Gate.ASK_MODEL.equals(entry.capability())) {
            if (!failure) {
                return false;
            }
            return entry.resultCode() != null && entry.resultCode().equals(doneWhen);
        }
        if (!failure) {
            return true;
        }
        return entry.resultCode() != null && entry.resultCode().equals(doneWhen);
    }
}
