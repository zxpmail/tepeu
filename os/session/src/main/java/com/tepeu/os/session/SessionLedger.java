package com.tepeu.os.session;

import com.tepeu.os.syscall.Usage;

import java.util.List;
import java.util.Map;

/**
 * 用量账本端口（ledger）— append-only；消耗从 ledger 派生，
 * 预算上限属 Metering/Policy 配置面（ADR-016 第五/七轮）。
 */
public interface SessionLedger {
    /** 记一笔用量，返回分配的账本序号（单调连续）。 */
    default long record(String syscallName, Usage usage) {
        return record(syscallName, usage, Map.of());
    }

    long record(String syscallName, Usage usage, Map<String, String> attrs);

    List<LedgerEntry> readAll();
}
