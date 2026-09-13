/**
 * 一次对话的持久状态。事件日志、寄存器、用量流水互斥；收件箱与操作审计另册。
 * 只依赖 persist-api。不关库。第一刀无列表、无 fork、无压缩面。
 */
package com.tepeu.session;
