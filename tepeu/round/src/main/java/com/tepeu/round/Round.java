package com.tepeu.round;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 跑一轮：只按顺序调用另外三个口，自己不写步骤账。
 * 走过的步骤写入过程账，供监控。判定不读过程账。装载在这里内部完成。
 */
public final class Round {

    private static final int RELEASE_LIMIT = 8;

    private final Base base;
    private final Gate gate;
    private final Judge judge;
    private final List<ProjectionLine> projection = new ArrayList<>();
    private final List<ProcessNote> process = new ArrayList<>();

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

    /** 跑完这一轮。没有做成与否的回执。modelUtterance 不入步骤账。过程写入过程账。 */
    public void run(Turn turn) {
        if (gate.stopped(turn.stopHit(), turn.entry())) {
            note(ProcessNote.STOP_CHECK, turn.entry() == Entry.side ? "旁问停" : "主轮停");
            return;
        }
        note(ProcessNote.STOP_CHECK, "继续");
        if (turn.entry() == Entry.side) {
            note(ProcessNote.SIDE, turn.askModel() ? "问模型" : "不问模型");
            load(false);
            noteLoad();
            if (turn.askModel()) {
                noteRelease(gate.release(Gate.ASK_MODEL, null, Entry.side));
            }
            return;
        }
        if (turn.pureChat()) {
            note(ProcessNote.CHAT, turn.askModel() ? "问模型" : "不问模型");
            load(false);
            noteLoad();
            if (turn.askModel()) {
                noteRelease(gate.release(Gate.ASK_MODEL, null, Entry.main));
            }
            return;
        }
        if (!gate.openable(turn.goal(), turn.limit(), turn.doneWhen())) {
            note(ProcessNote.CLOSED, "");
            return;
        }
        String goalId = base.openPiece(turn.goal().value(), turn.limit().value(), turn.doneWhen().value());
        note(ProcessNote.PIECE, goalId);
        load(true);
        noteLoad();
        int released = 0;
        for (String name : turn.calls()) {
            if (released >= RELEASE_LIMIT) {
                note(ProcessNote.SKIP, name);
                continue;
            }
            noteRelease(gate.release(name, goalId, Entry.main));
            released = released + 1;
        }
        note(ProcessNote.TO_JUDGE, goalId);
        judge.judge();
    }

    /** 读过程账。监控从这里看走过的步骤，不拿来判定做成。 */
    public List<ProcessNote> processLog() {
        return List.copyOf(process);
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

    /** 记下这一步。只进过程账。 */
    private void note(String step, String detail) {
        process.add(new ProcessNote(step, detail == null ? "" : detail));
    }

    /** 装载写进过程账。每一行标明不是人说的。 */
    private void noteLoad() {
        StringBuilder detail = new StringBuilder();
        for (ProjectionLine line : projection) {
            if (detail.length() > 0) {
                detail.append('、');
            }
            detail.append(line.text());
            if (!line.fromPerson()) {
                detail.append("·不是人说的");
            }
        }
        note(ProcessNote.LOAD, detail.toString());
    }

    /** 放行结果抄进过程账，不交给判定。 */
    private void noteRelease(ReleaseMark mark) {
        note(ProcessNote.RELEASE, mark.name() + " " + mark.outcome());
    }

    /** 没变就不再追加。 */
    private void appendIfChanged(ProjectionLine line) {
        if (projection.contains(line)) {
            return;
        }
        projection.add(line);
    }
}
