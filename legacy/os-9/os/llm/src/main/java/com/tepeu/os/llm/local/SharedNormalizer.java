package com.tepeu.os.llm.local;

import com.tepeu.os.llm.CanonicalRole;
import com.tepeu.os.llm.CanonicalTurn;

import java.util.ArrayList;
import java.util.List;

/** v1??? USER ??? */
public final class SharedNormalizer {

    public static final String VERSION = "1";

    private SharedNormalizer() {
    }

    public static List<CanonicalTurn> normalize(List<CanonicalTurn> turns) {
        List<CanonicalTurn> out = new ArrayList<>();
        for (CanonicalTurn turn : turns) {
            if (turn.role() == CanonicalRole.USER && !out.isEmpty()) {
                CanonicalTurn last = out.get(out.size() - 1);
                if (last.role() == CanonicalRole.USER) {
                    out.set(out.size() - 1, new CanonicalTurn(
                            last.role(), last.source(), last.body() + "\n" + turn.body(), last.attrs()));
                    continue;
                }
            }
            out.add(turn);
        }
        return List.copyOf(out);
    }
}
