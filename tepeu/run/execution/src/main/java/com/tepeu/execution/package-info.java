/**
 * 工作区文件与进程。第一刀隔离 = 路径圈禁（resolve+normalize 限 root 内），
 * 不追符号链接、无 OS 级沙箱；隔离程度由 probe 如实报告。
 * handler 级错误码与 dispatch 门码分家，详见 {@link com.tepeu.execution.Workspace}。
 */
package com.tepeu.execution;
