package com.tepeu.os.kernel.bus;

/**
 * 入口策略裁决（实现属② Policy 适配器）。
 */
public enum PolicyVerdict {
    ALLOW,
    DENY,
    NEED_APPROVAL
}
