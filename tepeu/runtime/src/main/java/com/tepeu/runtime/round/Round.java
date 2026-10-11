package com.tepeu.runtime.round;

import com.tepeu.runtime.gate.Gate;
import com.tepeu.runtime.gate.ReleaseMark;
import com.tepeu.runtime.judge.Judge;
import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.view.ShownLine;
import com.tepeu.runtime.view.View;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Entry;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.ToolAsk;

import java.util.ArrayList;
import java.util.List;

/**
 * 跑一轮。正题先问模型，只认回答旁边交来的工具，再按顺序放行。
 * 自己只写过程记录。不接收判断的结果。不读人交来的名单。
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
                noteRelease(gate.release(Ability.ASK, null, false, "", "", "", "", "", withSaid(shown, turn.said())));
            }
            return;
        }
        if (turn.pureChat()) {
            note(ProcessNote.CHAT, turn.askModel() ? "问模型" : "不问模型");
            List<String> shown = texts();
            noteLoad(shown);
            if (turn.askModel()) {
                noteRelease(gate.release(Ability.ASK, null, true, "", "", "", "", "", withSaid(shown, turn.said())));
            }
            return;
        }
        if (!gate.openable(turn.goal(), turn.limit(), turn.doneWhen())) {
            note(ProcessNote.CLOSED, "");
            return;
        }
        String goalId = ledger.openPiece(turn.goal().value(), turn.limit().value(), turn.doneWhen().value());
        note(ProcessNote.PIECE, goalId);
        int released = 0;
        while (released < RELEASE_LIMIT) {
            List<String> shown = withSaid(texts(), turn.said());
            noteLoad(shown);
            ReleaseMark asked = gate.release(
                    Ability.ASK,
                    goalId,
                    true,
                    "",
                    turn.limit().value(),
                    turn.goal().value(),
                    turn.doneWhen().value(),
                    "",
                    shown);
            noteRelease(asked);
            released = released + 1;
            if (!ResultKind.成功.name().equals(asked.outcome()) || asked.asks().isEmpty()) {
                break;
            }
            int next = releaseItems(asked.asks(), goalId, turn, shown, released);
            if (next < 0) {
                break;
            }
            released = next;
        }
        note(ProcessNote.TO_JUDGE, goalId);
        judge.judge();
    }

    /**
     * 按旁边交来的顺序放行。人拒绝或等超时，这一篇剩下的跳过，还可以再问。
     * 满 8 次则跳过剩下的，并且不再问。返回负数表示不要再问。
     */
    private int releaseItems(List<ToolAsk> items, String goalId, Turn turn, List<String> shown, int released) {
        int used = released;
        for (int index = 0; index < items.size(); index++) {
            if (used >= RELEASE_LIMIT) {
                skipFrom(items, index);
                return -1;
            }
            ToolAsk item = items.get(index);
            String target = item.target();
            if (target.isBlank() && (Ability.GREP.equals(item.name()) || Ability.GLOB.equals(item.name()))) {
                target = turn.limit().value();
            }
            ReleaseMark mark = gate.release(
                    item.name(),
                    goalId,
                    true,
                    target,
                    turn.limit().value(),
                    turn.goal().value(),
                    turn.doneWhen().value(),
                    item.handed(),
                    shown);
            noteRelease(mark);
            used = used + 1;
            if (Refusal.USER_DENIED.equals(mark.outcome()) || Refusal.APPROVAL_TIMEOUT.equals(mark.outcome())) {
                skipFrom(items, index + 1);
                return used;
            }
        }
        return used;
    }

    /** 从这一项起记跳过，不放行，也不写拒绝。有目标就一并记下。 */
    private void skipFrom(List<ToolAsk> items, int from) {
        for (int index = from; index < items.size(); index++) {
            ToolAsk item = items.get(index);
            String detail = item.target() == null || item.target().isBlank()
                    ? item.name()
                    : item.name() + " " + item.target();
            note(ProcessNote.SKIP, detail);
        }
    }

    /** 读过程记录。 */
    public List<ProcessNote> processLog() {
        return List.copyOf(process);
    }

    /** 读步骤记录。验收从这里读，不看这一次调用的返回。 */
    public List<Step> ledger() {
        return ledger.read();
    }

    /** 固定说明后面加上人刚说的这句。说明本身不改。 */
    private static List<String> withSaid(List<String> shown, String said) {
        if (said == null || said.isBlank()) {
            return shown;
        }
        List<String> lines = new ArrayList<>(shown);
        lines.add(said);
        return List.copyOf(lines);
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
