# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**develop 底板已重写**：实施以 `docs/os-baseplate.md` + `os/` 骨架为准；ADR-016 为规范。

## 上次停在哪
- ✅ 严苛补洞：Compaction 属缝、预算门、TurnContext、缝分类
- ✅ `docs/os-baseplate.md`；`os/{kernel,adaptors,orchestration,routing,compose}/`
- ✅ `legacy/` 只读；下一步按底板填 ① 会话+总线空实现（黄灯切片）

## 近期关键决定
- 双真相：会话日志 vs AuditSink
- Tool/MCP=②；Slash→Command；压缩走总线
- 详见 ADR-016 / os-baseplate
