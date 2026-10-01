package com.tepeu.round;

/**
 * 步骤账上的一笔。验收只读这些笔。
 * 关联薄底、调用门、判定做成。
 */
public record LedgerEntry(
        String kind,
        Writer writer,
        String refusalCode,
        String capability,
        String goalId,
        ResultKind resultKind,
        String resultCode,
        String body,
        String doneWhen,
        String limit
) {
    /** 停 */
    public static final String STOP = "停";
    /** 待补全 */
    public static final String AWAITING = "待补全";
    /** 拒绝码 */
    public static final String REFUSAL = "拒绝";
    /** 平台返回 */
    public static final String RETURN = "平台返回";
    /** 这一件 */
    public static final String PIECE = "这一件";
    /** 做成 */
    public static final String DONE = "做成";
    /** 未做成 */
    public static final String NOT_DONE = "未做成";

    /** 没装上 */
    public static final String NOT_INSTALLED = "NOT_INSTALLED";
    /** 程序拦住 */
    public static final String GATE_BLOCKED = "GATE_BLOCKED";
}
