package com.tepeu.os.policy;

/**
 * 入口策略裁决 — 封闭 union（红线 §2）；词汇表外一律规范化为拒绝（fail-closed）。
 */
public enum PolicyVerdict {
    ALLOW,
    DENY,
    NEED_APPROVAL
}
