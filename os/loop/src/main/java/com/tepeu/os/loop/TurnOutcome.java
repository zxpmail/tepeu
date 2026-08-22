package com.tepeu.os.loop;

import java.util.Objects;

/**
 * 一轮结果。COMPLETED 仅当完成证据门放行；SSE/idle 不是完成。
 */
public record TurnOutcome(Kind kind, int steps, String detail) {

    public TurnOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        if (steps < 0) {
            throw new IllegalArgumentException("steps < 0");
        }
    }

    public enum Kind {
        EMPTY,
        COMPLETED,
        FAILED,
        STOPPED,
        INCOMPLETE,
        INVALID
    }

    public boolean completed() {
        return kind == Kind.COMPLETED;
    }

    public static TurnOutcome empty() {
        return new TurnOutcome(Kind.EMPTY, 0, "inbox empty");
    }

    public static TurnOutcome completed(int steps) {
        return new TurnOutcome(Kind.COMPLETED, steps, "ok");
    }

    public static TurnOutcome failed(int steps, String detail) {
        return new TurnOutcome(Kind.FAILED, steps, detail);
    }

    public static TurnOutcome stopped(int steps, String detail) {
        return new TurnOutcome(Kind.STOPPED, steps, detail);
    }

    public static TurnOutcome incomplete(int steps, String detail) {
        return new TurnOutcome(Kind.INCOMPLETE, steps, detail);
    }

    public static TurnOutcome invalid(String detail) {
        return new TurnOutcome(Kind.INVALID, 0, detail);
    }
}
