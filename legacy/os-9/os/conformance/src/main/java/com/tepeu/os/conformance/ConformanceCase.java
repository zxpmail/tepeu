package com.tepeu.os.conformance;

import java.util.Objects;

/**
 * 一条 conformance 用例 — runner 无关（只依赖 JDK），消费者桥接到任意测试框架。
 * 载体随 {@code tepeu-os-conformance} 发布；组件套件在各模块 {@code src/test}，不进组件发行 jar。
 */
public record ConformanceCase(String group, String name, Runnable assertion) {
    public ConformanceCase {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(assertion, "assertion");
    }

    public String displayName() {
        return group + " :: " + name;
    }

    public void run() {
        assertion.run();
    }
}
