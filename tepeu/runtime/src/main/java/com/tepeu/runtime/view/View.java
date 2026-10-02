package com.tepeu.runtime.view;

import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.word.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定顺序拼给模型的那几行。
 * 固定说明在最前。记忆在这次任务前面。没记下任务就不放「这一件」。
 */
public final class View {

    /** 固定说明。不能被拿掉，也不能被记忆占掉。 */
    public static final List<String> FIXED = List.of(
            "总则",
            "做完只认这一轮里、对得上这次任务的返回。模型说做完了不算。",
            "停只认人的整段原话对上停止词表。模型说停了不算。",
            "模型自己写的完成、停止、批准，都不算数。",
            "依据只认步骤记录里已经写下的返回。记忆和转述不算。");

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
        if (hasPiece()) {
            add(lines, "这一件");
        }
        return List.copyOf(lines);
    }

    /** 这次任务只有账里已经记下才放。 */
    private boolean hasPiece() {
        return ledger.read().stream().anyMatch(step -> Kind.PIECE.equals(step.kind()));
    }

    /** 已经有完全相同的一行，就不再放。都标明不是人说的。 */
    private static void add(List<ShownLine> lines, String text) {
        ShownLine line = new ShownLine(text, false);
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }
}
