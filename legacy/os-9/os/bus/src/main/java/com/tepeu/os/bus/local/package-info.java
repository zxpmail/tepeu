/**
 * bus 本机默认实现。生产路径是 {@link com.tepeu.os.bus.local.LocalCapabilityBus}，不是 InMemory* 夹具。
 * 拦截（DENY / NEED_APPROVAL / 未装 policy / 无 handler / handler 错）走 JDK {@code System.Logger}，
 * 不打 ALLOW，不打 syscall args。
 */
package com.tepeu.os.bus.local;
