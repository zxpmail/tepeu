package com.tepeu.runtime.round;

/**
 * 人提交的一次调用。读、写、跑才用目标。
 */
public record Call(String name, String target) {

    /** 没写目标就是空。 */
    public Call {
        name = name == null ? "" : name;
        target = target == null ? "" : target;
    }

    /** 只有能力名。 */
    public static Call of(String name) {
        return new Call(name, "");
    }

    /** 能力名和这次要碰到的地方。 */
    public static Call of(String name, String target) {
        return new Call(name, target);
    }
}
