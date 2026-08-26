# Tepeu

develop 按 [ADR-016](memory/decisions-log.md) 重建 Agent OS **内核**。当前口径：**本机 Agent OS 骨架可演示**，**不是**企业 OS / 完整 OS——见 [`docs/agent-os-gap.md`](docs/agent-os-gap.md)。

可运行的 v1.0（工作台 / Harness）在 `main` / tag `v1.0.0`，标本在 [`legacy/`](./legacy/README.md)。v1 规格里的「Agentic OS」是产品里程碑名，不是「OS 已成形」。

## 分支

| 分支 | 用途 |
|------|------|
| `main` | v1.0 冻结线 |
| `develop` | 重写与新内核（当前） |

## 仓库布局（develop）

```
docs/os-baseplate.md   实施底板（必读）
docs/os-handbook.md    独有章（时序 / 开机 / 威胁）
docs/archive/          已吸入 ADR 的参照与 v1 草稿（不是规范）
os/                    新骨架（10 组件：session/policy/persist/observation/bus/llm/loop/orchestration/execution/compose）
legacy/                v1 只读标本
memory/                ADR（ADR-016 规范）
```

根目录 `Dockerfile` / `docker-compose.yml` 仍对应 **v1 布局**，请用 `main` 或 `legacy/` 运行旧版。

## 跑 v1（推荐）

```bash
git checkout main
cd backend && mvn spring-boot:run
# 另开终端
cd frontend && npm install && npm run dev
```

或在 develop 上：

```bash
cd legacy/backend && mvn spring-boot:run
cd legacy/frontend && npm install && npm run dev
```

## 文档入口

冲突时：**ADR-016 > 底板 > 手册 > v1 规格**。阅读序：`CONTEXT.md` → 底板 → [手册](./docs/os-handbook.md) → 差距 → ADR-016。

| 文件 | 用途 |
|------|------|
| [CONTEXT.md](./CONTEXT.md) | 当前进度与下一刀 |
| [docs/os-baseplate.md](./docs/os-baseplate.md) | develop OS 实施底板（必读） |
| [docs/os-handbook.md](./docs/os-handbook.md) | 独有章：时序 / 开机 / 威胁 / 术语 |
| [docs/agent-os-gap.md](./docs/agent-os-gap.md) | 诚实度：距 OS 还差什么 |
| [memory/decisions-log.md](./memory/decisions-log.md) | 规范真相（**ADR-016**） |
| [docs/archive/](./docs/archive/README.md) | 对账参照与 v1 草稿（不是规范） |
| [Product-Spec.md](./Product-Spec.md) | **v1** 产品规格（非 develop OS 规范） |
| [DEV-PLAN.md](./DEV-PLAN.md) | **v1** 交付切片档案（已冻结） |
| [legacy/README.md](./legacy/README.md) | v1 代码只读说明 |
| [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md) | v1.0.0 说明 |

## 已完成（v1 摘要）

- ✅ v0.1 工作台 · v0.2 Harness · v1.0 产品里程碑（规格名称含 Agentic OS，**≠** 完整 OS 已交付）
- ⏳ develop：按 ADR-016 重建内核（当前 = 本机 Agent OS 骨架可演示；隔离 partial）
