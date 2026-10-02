package com.tepeu.runtime.round;

/**
 * 过程记录上的一步。给人看这一轮走过哪里。
 * 判断不读这里，也不能拿来撑起做完。
 */
public record ProcessNote(String step, String detail) {

    /** 认停 */
    public static final String STOP_CHECK = "认停";
    /** 旁问 */
    public static final String SIDE = "旁问";
    /** 纯聊 */
    public static final String CHAT = "纯聊";
    /** 字段没过 */
    public static final String CLOSED = "未开跑";
    /** 记下这一件 */
    public static final String PIECE = "记下这一件";
    /** 装载 */
    public static final String LOAD = "装载";
    /** 放行 */
    public static final String RELEASE = "放行";
    /** 超过 8 次 */
    public static final String SKIP = "跳过";
    /** 交给判断 */
    public static final String TO_JUDGE = "交判定";
}
