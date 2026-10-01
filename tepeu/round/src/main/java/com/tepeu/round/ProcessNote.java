package com.tepeu.round;

/**
 * 过程账上的一步。给人监控这一轮走过哪里。
 * 判定做成不读这里，也不能拿来撑起做成。
 */
public record ProcessNote(String step, String detail) {

    /** 认停 */
    public static final String STOP_CHECK = "认停";
    /** 旁问 */
    public static final String SIDE = "旁问";
    /** 纯聊 */
    public static final String CHAT = "纯聊";
    /** 三字段没过，没有开跑 */
    public static final String CLOSED = "未开跑";
    /** 薄底记下这一件 */
    public static final String PIECE = "记下这一件";
    /** 装载 */
    public static final String LOAD = "装载";
    /** 经调用门放行 */
    public static final String RELEASE = "放行";
    /** 超过 8 次，没有放行 */
    public static final String SKIP = "跳过";
    /** 交给判定，不带回做成与否 */
    public static final String TO_JUDGE = "交判定";
}
