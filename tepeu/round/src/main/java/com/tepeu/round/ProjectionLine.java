package com.tepeu.round;

/**
 * 装载放进模型可见序列的一行。
 * fromPerson 为 false 表示不是人说的。
 */
public record ProjectionLine(String text, boolean fromPerson) {
}
