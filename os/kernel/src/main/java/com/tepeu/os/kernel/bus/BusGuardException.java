package com.tepeu.os.kernel.bus;

/**
 * 卫兵拒绝（拦截通道；调用方契约见 ADR-016 第九轮失败双通道）。
 */
public class BusGuardException extends RuntimeException {
    public BusGuardException(String message) {
        super(message);
    }
}
