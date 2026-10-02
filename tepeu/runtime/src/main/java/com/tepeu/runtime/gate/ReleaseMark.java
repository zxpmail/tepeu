package com.tepeu.runtime.gate;

/**
 * 这一次放行的结果，给一轮抄进过程记录。
 * 不是步骤记录，也不交给判断。
 */
public record ReleaseMark(String name, String outcome) {
}
