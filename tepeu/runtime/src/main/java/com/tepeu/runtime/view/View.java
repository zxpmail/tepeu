package com.tepeu.runtime.view;

import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.ResultKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定顺序拼给模型的那几行。
 * 固定说明在最前。记忆在这次任务前面。返回和拒绝跟在这次任务后面。
 */
public final class View {

    /** 固定说明。不能被拿掉，也不能被记忆占掉。 */
    public static final List<String> FIXED = List.of(
            "总则",
            "账上的做完只认 Write、Edit 或跑命令的成功。Read、Grep、Glob 的成功不算做完，但要看文件就去读。模型说做完了不算。",
            "停只认人的整段原话对上停止词表。模型说停了不算。",
            "模型自己写的完成、停止、批准，都不算数。",
            "依据只认步骤记录里已经写下的返回。记忆和转述不算。",
            "Read、Grep、Glob、Edit、Write、跑命令已经装上。要看代码就用 Read、Grep、Glob，不要叫人把代码贴过来。回答里的字不会被拿去执行。");

    private final Ledger ledger;
    private final Memory memory;

    /** 账用来看这次任务记了没有。记忆可以不交。 */
    public View(Ledger ledger, Memory memory) {
        this.ledger = ledger;
        this.memory = memory;
    }

    /** 从当时的账和记忆再取一遍。相同的一行不再追加。 */
    public List<ShownLine> compose() {
        List<ShownLine> lines = new ArrayList<>();
        for (String line : FIXED) {
            add(lines, line);
        }
        if (memory != null) {
            List<String> recalled = memory.recall();
            if (recalled != null) {
                for (String line : recalled) {
                    if (line == null || line.isBlank() || FIXED.contains(line) || "这一件".equals(line)) {
                        continue;
                    }
                    add(lines, line);
                }
            }
        }
        Step piece = latestPiece();
        if (piece != null) {
            add(lines, "这一件");
            showField(lines, "要做什么", piece.body());
            showField(lines, "限制", piece.limit());
            showField(lines, "怎样算做完", piece.doneWhen());
            for (Step step : ledger.read()) {
                if (!piece.goalId().equals(step.goalId())) {
                    continue;
                }
                if (Kind.RETURN.equals(step.kind()) && step.resultKind() == ResultKind.成功) {
                    showBody(lines, step.body());
                } else if (Kind.RETURN.equals(step.kind()) && step.resultKind() != null) {
                    lines.add(new ShownLine("不是人说的 " + step.resultKind().name(), false));
                    showBody(lines, step.body());
                } else if (Kind.REFUSAL.equals(step.kind()) && step.refusalCode() != null) {
                    lines.add(new ShownLine("不是人说的 " + step.refusalCode(), false));
                }
            }
        }
        return List.copyOf(lines);
    }

    /** 最近记下的那一件。别的任务的返回不放。 */
    private Step latestPiece() {
        Step piece = null;
        for (Step step : ledger.read()) {
            if (Kind.PIECE.equals(step.kind()) && step.goalId() != null) {
                piece = step;
            }
        }
        return piece;
    }

    /** 任务的一句原文，标明不是人说的。 */
    private static void showField(List<ShownLine> lines, String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        lines.add(new ShownLine("不是人说的 " + label + " " + text, false));
    }

    /** 成功正文按原行放上，每一行前面标明不是人说的。 */
    private static void showBody(List<ShownLine> lines, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        for (String line : body.split("\\R", -1)) {
            lines.add(new ShownLine("不是人说的 " + line, false));
        }
    }

    /** 已经有完全相同的一行，就不再放。都标明不是人说的。 */
    private static void add(List<ShownLine> lines, String text) {
        ShownLine line = new ShownLine(text, false);
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }
}
