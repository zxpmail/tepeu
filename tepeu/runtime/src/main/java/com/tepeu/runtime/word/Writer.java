package com.tepeu.runtime.word;

/**
 * 步骤记录的写入者。一轮不在此列。
 * 账用 base，检查口用 gate，判断用 judge。
 */
public enum Writer {
    /** 检查口写下的那一笔 */
    gate,
    /** 账写下的那一笔 */
    base,
    /** 判断写下的那一笔 */
    judge
}
