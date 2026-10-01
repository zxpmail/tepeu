package com.tepeu.round;

/**
 * 一次放行的结果，只给过程账。
 * 不是步骤账，也不能交给判定做成。
 */
public record ReleaseMark(String name, String outcome) {
}
