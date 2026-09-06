package com.tepeu.os.loop;

/**
 * Loop 三态 — 控制态进 registers（`loop.state`），不进事件词汇表。
 */
public enum LoopState {
    IDLE,
    RUNNING,
    MAINTENANCE
}
