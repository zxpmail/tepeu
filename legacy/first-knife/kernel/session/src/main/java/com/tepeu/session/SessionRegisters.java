package com.tepeu.session;

import java.util.Map;
import java.util.Optional;

/**
 * 覆盖写的小状态。例如循环是否空闲。不是对话正文。
 */
public interface SessionRegisters {

    Optional<String> get(String key);

    void put(String key, String value);

    Map<String, String> snapshot();
}
