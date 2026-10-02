package com.tepeu.runtime.word;

/**
 * 人提交的一个字段。没有运行。
 * 没提交和提交了但是空白，后面由字段那一类分开认。
 */
public record Field(boolean present, String value) {

    /** 这个字段没有提交。 */
    public static Field missing() {
        return new Field(false, "");
    }

    /** 这个字段已提交，字按原样留下。 */
    public static Field of(String value) {
        return new Field(true, value == null ? "" : value);
    }
}
