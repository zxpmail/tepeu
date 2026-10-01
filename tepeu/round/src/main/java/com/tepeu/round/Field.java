package com.tepeu.round;

/**
 * 人提交的一个字段。
 * 键没提交是待补全；键在但去掉空白后为空是脏单。
 */
public record Field(boolean present, String value) {

    /** 这个键没有提交。 */
    public static Field missing() {
        return new Field(false, "");
    }

    /** 这个键已提交，值按原样保留。 */
    public static Field of(String value) {
        return new Field(true, value == null ? "" : value);
    }
}
