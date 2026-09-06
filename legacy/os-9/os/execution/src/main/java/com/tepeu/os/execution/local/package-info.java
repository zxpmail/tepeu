/**
 * execution 本机默认实现（fs/proc handler、囚笼、{@link com.tepeu.os.execution.local.OsJails} 探测）。syscall 名常量表留在父包。
 * jail 拒绝 / spawn 失败走 JDK {@code System.Logger}，不打 argv / stdout。
 */
package com.tepeu.os.execution.local;
