package com.tepeu.os.session;

import java.util.Optional;

/**
 * registers：覆盖写可变状态；恢复=点查非重放（ADR-016 第五轮）。
 * 编排控制态（如 loop.state）走这里，不进事件词汇表。
 */
public interface SessionRegisters {
    Optional<String> get(String key);

    void put(String key, String value);
}
