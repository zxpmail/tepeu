package com.tepeu.runtime.word;

/**
 * 能力交回的结果种类。只有这四种。
 * 检查口把种类抄进步骤记录，判断按种类看能不能算做完。
 */
public enum ResultKind {
    /** 成功 */
    成功,
    /** 失败 */
    失败,
    /** 拒绝 */
    拒绝,
    /** 超时 */
    超时
}
