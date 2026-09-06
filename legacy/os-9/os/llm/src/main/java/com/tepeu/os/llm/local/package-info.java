/**
 * llm 本机默认实现（给模型看什么、协议投影、传输、generate handler、{@link com.tepeu.os.llm.local.LlmTransports#prepare}）。端口在父包。
 * 传输失败与上笔 digest 失配走 JDK {@code System.Logger}，不打 wire / 密钥 / digest 明文。
 */
package com.tepeu.os.llm.local;
