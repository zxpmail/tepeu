package com.tepeu.os.kernel.bus;

/**
 * 策略拒绝或待审批。
 */
public class PolicyDeniedException extends RuntimeException {
    private final PolicyVerdict verdict;

    public PolicyDeniedException(PolicyVerdict verdict, String message) {
        super(message);
        this.verdict = verdict;
    }

    public PolicyVerdict verdict() {
        return verdict;
    }
}
