package com.tepeu.round;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 挂在薄底上的步骤账。四个口共用这一本，但各自只写自己的笔。
 * 不是第五个口。
 */
final class Ledger {

    private final List<LedgerEntry> entries = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    /** 追加一笔。已有人在写时，这一笔不记下，并明确失败。 */
    void append(LedgerEntry entry) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("步骤账正在写入，这一笔没有记下");
        }
        try {
            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    /** 读回全部。与写入共用同一把锁，读的时候不能同时改。 */
    List<LedgerEntry> read() {
        lock.lock();
        try {
            return List.copyOf(entries);
        } finally {
            lock.unlock();
        }
    }

    /** 占住账，供测试确认第二笔同时写入不会记下。 */
    void runLocked(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
