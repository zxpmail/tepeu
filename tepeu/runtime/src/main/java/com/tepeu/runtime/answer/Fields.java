package com.tepeu.runtime.answer;

import com.tepeu.runtime.word.Field;

/**
 * 回答三个字段是缺了、不合格，还是可以开始。
 * 不从原话里抽字段，也不写步骤记录。
 */
public final class Fields {

    /** 字段给出的三种答案。 */
    public enum Answer {
        /** 还有字段没提交 */
        还没填齐,
        /** 提交了，但是不合格 */
        不合格,
        /** 三个字段都可以开始 */
        可以开始
    }

    /** 看要做什么、限制、怎样算做完。 */
    public Answer look(Field goal, Field limit, Field doneWhen) {
        if (goal == null || limit == null || doneWhen == null
                || !goal.present() || !limit.present() || !doneWhen.present()) {
            return Answer.还没填齐;
        }
        if (bad(goal.value()) || bad(limit.value()) || "按上面说的做".equals(goal.value())) {
            return Answer.不合格;
        }
        if ("完成".equals(doneWhen.value()) || "done".equals(doneWhen.value())) {
            return Answer.不合格;
        }
        return Answer.可以开始;
    }

    /** 去掉空白后一个字都没有，算不合格。不忽略大小写和标点。 */
    private static boolean bad(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
