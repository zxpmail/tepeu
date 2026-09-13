/**
 * 控制循环与完成判定。事件日志与用量流水的唯一写入者（assistant 事件归这里）。
 * 经 dispatch 按名调模型与工具；不知道 llm / execution 的实现类型。
 * 最小工具请求协议见 {@link com.tepeu.loop.ToolRequest}。
 */
package com.tepeu.loop;
