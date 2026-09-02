/**
 * llm 本机默认实现（fake / HTTP 传输、协议投影、generate handler）。端口在父包。
 * 传输失败与上笔 digest 失配走 JDK {@code System.Logger}，不打 wire / 密钥 / digest 明文。
 */
package com.tepeu.os.llm.local;
