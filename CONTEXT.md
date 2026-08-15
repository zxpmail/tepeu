# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**develop 重写骨架阶段**：旧业务在 `legacy/`；架构以 **ADR-016** 为准，`docs/kernel-layer.md` 仅为图示投影。

## 上次停在哪
- ✅ v1 → `legacy/`；main 冻结
- ✅ ADR-016 裁决：Tool/MCP=②插头；人手→AuditSink；压缩走总线+卫兵+计量；Slash≠人手旁路；底板=内核三件
- ⏳ 下一步：按 ADR 落空骨架，勿在 `legacy/` 加功能

## 近期关键决定
- 五环由内向外：内核 → 适配插头 → 编排 → 路由 → 应用；人机旁路对称过 Policy+卫兵
- 双真相：会话日志（对话/工具）≠ AuditSink（人手审计）
- 详见 ADR-016；图：`docs/kernel-layer.md`
