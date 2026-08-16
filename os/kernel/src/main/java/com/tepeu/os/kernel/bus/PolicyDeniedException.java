package com.tepeu.os.kernel.bus;

/**
 * 策略拒绝（拦截通道；含 NEED_APPROVAL 在无通道/被否决时的 fail-closed 形态）。
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
