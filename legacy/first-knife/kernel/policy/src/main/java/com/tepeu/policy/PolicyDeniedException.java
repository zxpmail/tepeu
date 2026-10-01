package com.tepeu.policy;

/** 策略拒绝（拦截通道）。调用方 catch，不写事件日志。 */
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
