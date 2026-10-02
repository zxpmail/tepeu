package com.tepeu.runtime.view;

/**
 * 给模型看的一行。程序放进去的都标明不是人说的。
 */
public record ShownLine(String text, boolean fromPerson) {
}
