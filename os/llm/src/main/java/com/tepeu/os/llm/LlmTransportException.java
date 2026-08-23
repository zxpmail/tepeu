package com.tepeu.os.llm;

/**
 * 传输失败可见 — 不抛穿成 handler 崩溃；由 {@link LlmGenerateHandler} 收成 ok=false。
 */
public final class LlmTransportException extends RuntimeException {

    private final String errorCode;

    public LlmTransportException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? "TRANSPORT" : errorCode;
    }

    public LlmTransportException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? "TRANSPORT" : errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
