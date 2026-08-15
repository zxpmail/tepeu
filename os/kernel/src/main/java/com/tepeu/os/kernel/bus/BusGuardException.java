package com.tepeu.os.kernel.bus;

/**
 * 卫兵拒绝。
 */
public class BusGuardException extends RuntimeException {
    public BusGuardException(String message) {
        super(message);
    }
}
