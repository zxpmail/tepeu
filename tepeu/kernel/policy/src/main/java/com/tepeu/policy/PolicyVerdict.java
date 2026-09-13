package com.tepeu.policy;

/** 入口裁决，封闭三值。词汇表外一律按 {@code DENY} 处理。 */
public enum PolicyVerdict {
    ALLOW,
    DENY,
    NEED_APPROVAL
}
