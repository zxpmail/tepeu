package com.tepeu.runtime.round;

import com.tepeu.runtime.gate.Gate;
import com.tepeu.runtime.gate.ReleaseMark;
import com.tepeu.runtime.judge.Judge;
import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.view.ShownLine;
import com.tepeu.runtime.view.View;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Entry;

import java.util.ArrayList;
import java.util.List;

/**
 * 跑一轮。只按次序去问账、检查口、给模型看、判断。
 * 自己只写过程记录。不接收判断的结果。
 */
public final class Round {

    private static final int RELEASE_LIMIT = 8;

    private final Ledger ledger;
    private final Gate gate;
    private final Judge judge;
    private final View view;
    private final List<ProcessNote> process = new ArrayList<>();

    /** 四个部分已经接好。 */
    public Round(Ledger ledger, Gate gate, Judge judge, View view) {
        this.ledger = ledger;
        this.gate = gate;
        this.judge = judge;
        this.view = view;
    }

    /** 跑完这一轮。没有做完与否的回执。模型那句话不入步骤记录。 */
    public void run(Turn turn) {
        process.clear();
        boolean writeDown = turn.entry() != Entry.side;
        if (gate.stopped(turn.stopHit(), turn.said(), writeDown)) {
            note(ProcessNote.STOP_CHECK, turn.entry() == Entry.side ? "旁问停" : "主轮停");
            return;
        }
        note(ProcessNote.STOP_CHECK, "继续");
        if (turn.entry() == Entry.side) {
            note(ProcessNote.SIDE, turn.askModel() ? "问模型" : "不问模型");
            List<String> shown = texts();
            noteLoad(shown);
            if (turn.askModel()) {
                noteRelease(gate.release(Ability.ASK, null, false, "", "", "", "", shown));
            }
            return;
        }
        if (turn.pureChat()) {
            note(ProcessNote.CHAT, turn.askModel() ? "问模型" : "不问模型");
            List<String> shown = texts();
            noteLoad(shown);
            if (turn.askModel()) {
                noteRelease(gate.release(Ability.ASK, null, true, "", "", "", "", shown));
            }
            return;
        }
        if (!gate.openable(turn.goal(), turn.limit(), turn.doneWhen())) {
            note(ProcessNote.CLOSED, "");
            return;
        }
        String goalId = ledger.openPiece(turn.goal().value(), turn.limit().value(), turn.doneWhen().value());
        note(ProcessNote.PIECE, goalId);
        List<String> shown = texts();
        noteLoad(shown);
        int released = 0;
        for (Call call : turn.calls()) {
            if (released >= RELEASE_LIMIT) {
                note(ProcessNote.SKIP, call.name());
                continue;
            }
            noteRelease(gate.release(
                    call.name(),
                    goalId,
                    true,
                    call.target(),
                    turn.limit().value(),
                    turn.goal().value(),
                    turn.doneWhen().value(),
                    shown));
            released = released + 1;
        }
        note(ProcessNote.TO_JUDGE, goalId);
        judge.judge();
    }

    /** 读过程记录。 */
    public List<ProcessNote> processLog() {
        return List.copyOf(process);
    }

    /** 读步骤记录。验收从这里读，不看这一次调用的返回。 */
    public List<com.tepeu.runtime.ledger.Step> ledger() {
        return ledger.read();
    }

    /** 给模型看交回的字。原样拿去放行。 */
    private List<String> texts() {
        List<String> lines = new ArrayList<>();
        for (ShownLine line : view.compose()) {
            lines.add(line.text());
        }
        return List.copyOf(lines);
    }

    /** 装载写进过程记录。每一行标明不是人说的。 */
    private void noteLoad(List<String> lines) {
        StringBuilder detail = new StringBuilder();
        for (String line : lines) {
            if (detail.length() > 0) {
                detail.append('、');
            }
            detail.append(line).append("·不是人说的");
        }
        note(ProcessNote.LOAD, detail.toString());
    }

    /** 放行结果抄进过程记录。 */
    private void noteRelease(ReleaseMark mark) {
        note(ProcessNote.RELEASE, mark.name() + " " + mark.outcome());
    }

    /** 记下这一步。 */
    private void note(String step, String detail) {
        process.add(new ProcessNote(step, detail == null ? "" : detail));
    }
}
