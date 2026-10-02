package com.tepeu.runtime.answer;

import java.time.Duration;

/**
 * 回答这一次、这一份内容同不同意。不写步骤记录。
 * 同意不留下次。过程出错时把异常交回去，由检查口记成拦住，而不是超时。
 */
public final class Consent {

    /** 默认等这么久。不能改成 0。 */
    public static final Duration WAIT = Duration.ofSeconds(60);

    private final Decider decider;

    /** 这一次怎么回答，在交进来时定死。 */
    public Consent(Decider decider) {
        if (decider == null) {
            throw new IllegalArgumentException("点头要有回答的办法");
        }
        this.decider = decider;
    }

    /** 问这一次、这一份。每次都重新问，不沿用上次的同意。 */
    public Answer answer(String name, String content) {
        Answer given = decider.decide(name, content == null ? "" : content);
        if (given == null) {
            return Answer.没有结果;
        }
        return given;
    }

    /** 点头的三种结果。出错不是这三种，出错是抛出去。 */
    public enum Answer {
        /** 人同意 */
        同意,
        /** 人拒绝 */
        拒绝,
        /** 到点没有结果 */
        没有结果
    }

    /** 外面交进来的回答办法。可以抛异常，表示这一次过程出错。 */
    @FunctionalInterface
    public interface Decider {
        /** 按能力名和这一份内容给出结果。 */
        Answer decide(String name, String content);
    }
}
