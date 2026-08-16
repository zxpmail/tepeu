package com.tepeu.os.kernel.conformance;

import java.util.Objects;

/**
 * 一条 conformance 用例 — runner 无关（只依赖 JDK），消费者桥接到任意测试框架。
 * 随包发布（Pi /testing 四件套先例）：任何 ② 实现必须过同一套件。
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
