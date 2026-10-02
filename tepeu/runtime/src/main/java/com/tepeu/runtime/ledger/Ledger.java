package com.tepeu.runtime.ledger;

import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Writer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 这一本账。装好时记下是谁，三个字段合格之后分配任务编号。
 * 检查口和判断来追加他们自己的笔。同一时刻只允许一条追加成功。
 */
public final class Ledger {

    private final List<Step> entries = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int sequence;

    /** 装好这一本账，立刻记下这一次使用的人。没有登录名。 */
    public Ledger() {
        append(new Step(
                Kind.WHO,
                Writer.base,
                null,
                null,
                null,
                null,
                null,
                "这一次使用的人",
                null,
                null));
    }

    /**
     * 记下这次任务。不检查这三个字段合不合格。
     * 原文留在这一件上，编号交回调用的人。
     */
    public String openPiece(String goal, String limit, String doneWhen) {
        sequence = sequence + 1;
        String goalId = "g" + sequence;
        append(new Step(
                Kind.PIECE,
                Writer.base,
                null,
                null,
                goalId,
                null,
                null,
                goal,
                doneWhen,
                limit));
        return goalId;
    }

    /** 追加一笔。已有人在写时，这一笔记不下来。 */
    public void append(Step entry) {
        if (!lock.tryLock()) {
            throw new IllegalStateException("步骤记录正在写入，这一笔没有记下");
        }
        try {
            entries.add(entry);
        } finally {
            lock.unlock();
        }
    }

    /** 读回全部步骤记录。 */
    public List<Step> read() {
        lock.lock();
        try {
            return List.copyOf(entries);
        } finally {
            lock.unlock();
        }
    }

    /** 占住这本账，用来确认第二笔同时写记不下来。 */
    public void runLocked(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
