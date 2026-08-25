package com.tepeu.os.session;

import java.util.Map;
import java.util.Optional;

/**
 * registers — 覆盖写可变状态；恢复 = 点查非重放（ADR-016 第五轮）。
 * 编排控制态（{@code loop.state}、claim、delegationDepth、{@link SurfaceEpoch} 等）走这里，
 * <b>不</b>进事件词汇表（配置/控制禁入 entries）。
 */
public interface SessionRegisters {
    Optional<String> get(String key);

    void put(String key, String value);

    Map<String, String> snapshot();
}
