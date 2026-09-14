/**
 * Anthropic Messages API 实现，挂在网关后。协议与传输收在这里：
 * JDK HttpClient 直发 {@code /v1/messages}，jackson 序列化，60s 超时不重试。
 * 纯函数 {@code buildRequest} / {@code parseResponse} 可测；失败合成 {@code LLM_HTTP_<n>} / {@code LLM_IO_ERROR}。
 */
package com.tepeu.llm.anthropic;
