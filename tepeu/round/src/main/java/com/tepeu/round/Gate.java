package com.tepeu.round;

import java.util.Set;

/**
 * 调用门：认停、看三字段、放行。
 * 拒绝码和平台返回只由这里写。不分配编号，不写做成。
 */
public final class Gate {

    /** 问模型这个能力名。 */
    public static final String ASK_MODEL = "问模型";

    private static final String POINTER = "按上面说的做";
    private static final String RECEIPT_CN = "完成";
    private static final String RECEIPT_EN = "done";

    private final Ledger ledger;
    private final Set<String> installed;
    private final boolean feeInstalled;
    private final ReplySource replies;

    /** 装没装和费用在不在，在组成程序时定死。 */
    Gate(Ledger ledger, Set<String> installed, boolean feeInstalled, ReplySource replies) {
        this.ledger = ledger;
        this.installed = Set.copyOf(installed);
        this.feeInstalled = feeInstalled;
        this.replies = replies;
    }

    /** 认停。主轮记下停；旁问不写主账。 */
    public boolean stopped(boolean stopHit, Entry entry) {
        if (!stopHit) {
            return false;
        }
        if (entry == Entry.main) {
            ledger.append(row(LedgerEntry.STOP, null, null, null, null, null, null));
        }
        return true;
    }

    /** 看三字段。缺键记待补全；空白或闭集记拦住。 */
    public boolean openable(Field goal, Field limit, Field doneWhen) {
        if (!goal.present() || !limit.present() || !doneWhen.present()) {
            ledger.append(row(LedgerEntry.AWAITING, null, null, null, null, null, null));
            return false;
        }
        if (blank(goal.value()) || blank(limit.value()) || blank(doneWhen.value())
                || POINTER.equals(goal.value())
                || RECEIPT_CN.equals(doneWhen.value())
                || RECEIPT_EN.equals(doneWhen.value())) {
            ledger.append(row(LedgerEntry.REFUSAL, LedgerEntry.GATE_BLOCKED, null, null, null, null, null));
            return false;
        }
        return true;
    }

    /** 放行。旁问不写主账。传入了编号才抄到平台返回上。 */
    public void release(String name, String goalId, Entry entry) {
        if (!installed.contains(name)) {
            writeRefusal(entry, LedgerEntry.NOT_INSTALLED);
            return;
        }
        if (ASK_MODEL.equals(name) && !feeInstalled) {
            writeRefusal(entry, LedgerEntry.GATE_BLOCKED);
            return;
        }
        CapabilityReply reply = replies.take(name);
        if (reply == null) {
            reply = CapabilityReply.success();
        }
        if (entry == Entry.side) {
            return;
        }
        ledger.append(row(
                LedgerEntry.RETURN,
                null,
                name,
                goalId,
                reply.kind(),
                reply.resultCode(),
                reply.body()));
    }

    /** 主轮才把拒绝码写入主账。 */
    private void writeRefusal(Entry entry, String code) {
        if (entry == Entry.side) {
            return;
        }
        ledger.append(row(LedgerEntry.REFUSAL, code, null, null, null, null, null));
    }

    /** 去掉空白后为空。不改大小写和标点。 */
    private static boolean blank(String value) {
        return value == null || value.strip().isEmpty();
    }

    /** 这一笔的写入者固定是调用门。 */
    private static LedgerEntry row(
            String kind,
            String refusalCode,
            String capability,
            String goalId,
            ResultKind resultKind,
            String resultCode,
            String body) {
        return new LedgerEntry(kind, Writer.gate, refusalCode, capability, goalId, resultKind, resultCode, body, null, null);
    }
}
