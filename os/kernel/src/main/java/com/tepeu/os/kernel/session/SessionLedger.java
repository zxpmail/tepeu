package com.tepeu.os.kernel.session;

import com.tepeu.os.kernel.bus.Usage;

import java.util.List;

/**
 * 用量账本端口（ledger）— append-only；消耗从 ledger 派生，
 * 预算上限属 Metering/Policy 配置面（ADR-016 第五/七轮）。
 */
public interface SessionLedger {
    /** 记一笔用量，返回分配的账本序号（单调连续）。 */
    long record(String syscallName, Usage usage);

    List<LedgerEntry> readAll();
}
