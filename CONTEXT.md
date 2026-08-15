# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**develop 重写骨架阶段**：旧业务已迁入 `legacy/`；根目录保留规格/ADR/记忆，待按 ADR-016 建新内核。

## 上次停在哪
- ✅ `backend` / `frontend` / `experiments` / `scripts` → `legacy/`
- ✅ `main` 仍冻在 `c98fec8`；tag `v1.0.0` 仍在
- ⏳ 下一步：按 ADR-016 落空骨架（内核 3 件 + Adaptor 缝），勿在 `legacy/` 加功能

## 近期关键决定
- develop = 重写线；main = v1 标本
- 旧代码整树进 `legacy/`（只读），不直接删历史
- 严格内核：主体+命名空间、能力总线、会话事实日志；Loop 是运行时不是内核
- 企业可卖先薄；开发可活、固化求稳准效率
- 详见 ADR-016
