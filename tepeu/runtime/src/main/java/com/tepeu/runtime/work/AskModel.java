package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.util.List;

/**
 * 问模型。把已经准备好的几行原样发出去，把回答交回来。
 * 真连的时候读本机 cc-switch，当前这一家是 Xiaomi MiMo。没有字也原样交回，由检查口改记失败。
 */
public final class AskModel implements Job {

    private final Asker asker;

    /** 接上本机 cc-switch 里的 Xiaomi MiMo。没有这一家就装不上。 */
    public static AskModel mimo() {
        return new AskModel(new MimoAsker(CcSwitch.requireMimo()));
    }

    /** 问法在交进来时定死。 */
    public AskModel(Asker asker) {
        if (asker == null) {
            throw new IllegalArgumentException("问模型要有问法");
        }
        this.asker = asker;
    }

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.ASK;
    }

    /** 原样交出那几行，不另编，也不从回答里再找能力名。 */
    @Override
    public Reply run(Attempt attempt) {
        Reply reply = asker.ask(attempt.shown());
        if (reply == null) {
            return new Reply(ResultKind.失败, null, "");
        }
        return reply;
    }

    /** 外面交进来的问法。 */
    @FunctionalInterface
    public interface Asker {
        /** 发出这几行，交回答复。 */
        Reply ask(List<String> lines);
    }
}
