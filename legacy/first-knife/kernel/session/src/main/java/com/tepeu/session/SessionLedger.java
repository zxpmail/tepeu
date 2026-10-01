package com.tepeu.session;

import com.tepeu.syscall.Usage;

import java.util.List;

/**
 * 用量流水。只追加。自己不喊停。
 */
public interface SessionLedger {

    long record(String syscallName, Usage usage);

    List<LedgerEntry> readAll();
}
