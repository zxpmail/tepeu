package com.tepeu.llm.gateway;

import com.tepeu.syscall.SyscallResult;

import java.util.List;

/**
 * 网关后的实现口。实现做协议与传输；第一刀 fake，以后真模型。
 * 不得返回 {@code null}。
 */
@FunctionalInterface
public interface LlmBackend {

    SyscallResult generate(List<LlmMessage> visible);
}
