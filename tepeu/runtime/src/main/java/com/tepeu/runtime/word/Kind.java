package com.tepeu.runtime.word;

/**
 * 步骤记录里每一笔的格子名。
 * 账、检查口、判断只往这些名字上写，不另造一种。
 */
public final class Kind {

    /** 这一次使用的人 */
    public static final String WHO = "谁";
    /** 停 */
    public static final String STOP = "停";
    /** 还没填齐 */
    public static final String AWAITING = "待补全";
    /** 拒绝 */
    public static final String REFUSAL = "拒绝";
    /** 能力交回的结果 */
    public static final String RETURN = "平台返回";
    /** 这次任务 */
    public static final String PIECE = "这一件";
    /** 做完 */
    public static final String DONE = "做成";
    /** 没做完 */
    public static final String NOT_DONE = "未做成";
    /** 执行登记。里面长什么样，这一轮不定 */
    public static final String ARRANGED_MARK = "执行登记";
    /** 已安排。没有执行登记就不能写 */
    public static final String ARRANGED = "已安排";

    private Kind() {
    }
}
