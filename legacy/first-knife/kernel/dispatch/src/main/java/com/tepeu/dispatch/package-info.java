/**
 * 名称 → 处理函数 的注册表与一次调用的唯一门。
 * 一切失败合成 {@code SyscallResult}，不抛异常。看不见 session，不写日志。
 */
package com.tepeu.dispatch;
