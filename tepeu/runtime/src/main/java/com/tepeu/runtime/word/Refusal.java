package com.tepeu.runtime.word;

/**
 * 四种拒绝的名字。没有第五种。
 * 检查口只写这四个字，判断和一轮按这些字来认。
 */
public final class Refusal {

    /** 人拒绝了 */
    public static final String USER_DENIED = "USER_DENIED";
    /** 没装上，不许做 */
    public static final String NOT_INSTALLED = "NOT_INSTALLED";
    /** 等同意超时 */
    public static final String APPROVAL_TIMEOUT = "APPROVAL_TIMEOUT";
    /** 程序拦住了 */
    public static final String GATE_BLOCKED = "GATE_BLOCKED";

    private Refusal() {
    }
}
