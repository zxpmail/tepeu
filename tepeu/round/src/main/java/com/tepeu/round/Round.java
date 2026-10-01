package com.tepeu.round;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 跑一轮：只按顺序调用另外三个口，自己不写步骤账。
 * 装载在这里内部完成。
 */
public final class Round {

    private static final int RELEASE_LIMIT = 8;

    private final Base base;
    private final Gate gate;
    private final Judge judge;
    private final List<ProjectionLine> projection = new ArrayList<>();

    /**
     * 组成这一轮程序。
     * installed 与 feeInstalled 在这里定死，不随每一轮传入。
     */
    public Round(Set<String> installed, boolean feeInstalled, ReplySource replies) {
        Ledger ledger = new Ledger();
        this.base = new Base(ledger);
        this.gate = new Gate(ledger, installed, feeInstalled, replies);
        this.judge = new Judge(ledger);
    }

    /** 跑完这一轮。没有做成与否的回执。modelUtterance 不入账。 */
    public void run(Turn turn) {
        if (gate.stopped(turn.stopHit(), turn.entry())) {
            return;
        }
        if (turn.entry() == Entry.side) {
            load(false);
            if (turn.askModel()) {
                gate.release(Gate.ASK_MODEL, null, Entry.side);
            }
            return;
        }
        if (turn.pureChat()) {
            load(false);
            if (turn.askModel()) {
                gate.release(Gate.ASK_MODEL, null, Entry.main);
            }
            return;
        }
        if (!gate.openable(turn.goal(), turn.limit(), turn.doneWhen())) {
            return;
        }
        String goalId = base.openPiece(turn.goal().value(), turn.limit().value(), turn.doneWhen().value());
        load(true);
        int released = 0;
        for (String name : turn.calls()) {
            if (released >= RELEASE_LIMIT) {
                break;
            }
            gate.release(name, goalId, Entry.main);
            released = released + 1;
        }
        judge.judge();
    }

    /** 读步骤账。验收从这里读，不看跑一轮的返回。 */
    public List<LedgerEntry> ledger() {
        return base.readLedger();
    }

    /** 模型可见的投影。不进步骤账。 */
    public List<ProjectionLine> projection() {
        return List.copyOf(projection);
    }

    /** 装载。每一行标明不是人说的。相同内容不再追加。 */
    private void load(boolean withPiece) {
        appendIfChanged(new ProjectionLine("总则", false));
        if (withPiece) {
            appendIfChanged(new ProjectionLine("这一件", false));
        }
    }

    /** 没变就不再追加。 */
    private void appendIfChanged(ProjectionLine line) {
        if (projection.contains(line)) {
            return;
        }
        projection.add(line);
    }
}
