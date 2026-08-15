# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**develop ① 内核端口 + ② 内存冒烟已落地**；实施以 `docs/os-baseplate.md` + `os/` 为准。

## 上次停在哪
- ✅ 内核 Java 端口：identity / context / session / bus
- ✅ 内存适配器 + `KernelPortsSmokeTest`（`mvn -f os/pom.xml test` 通过）
- 下一步：黄灯切片补单机行为，或进 ③ 编排空壳（仍不搬 legacy）

## 近期关键决定
- 双真相：会话日志 vs AuditSink
- Tool/MCP=②；Slash→Command；压缩走总线
- 详见 ADR-016 / os-baseplate
