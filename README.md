# Tepeu

Agentic OS（智能体操作系统）。**develop 正在按 ADR-016 重写**；可运行的 v1.0 在 `main` / tag `v1.0.0`，旧代码标本在 [`legacy/`](./legacy/README.md)。

## 分支

| 分支 | 用途 |
|------|------|
| `main` | v1.0 冻结线 |
| `develop` | 重写与新内核（当前） |

## 仓库布局（develop）

```
docs/os-baseplate.md   实施底板（必读）
docs/kernel-layer.md   短图投影
os/                    新骨架（kernel/adaptors/orchestration/routing/compose）
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

| 文件 | 用途 |
|------|------|
| [Product-Spec.md](./Product-Spec.md) | 产品规格 |
| [CONTEXT.md](./CONTEXT.md) | 当前进度 |
| [memory/decisions-log.md](./memory/decisions-log.md) | ADR（含 **ADR-016**） |
| [legacy/README.md](./legacy/README.md) | 旧代码只读说明 |
| [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md) | v1.0.0 说明 |

## 已完成（v1 摘要）

- ✅ v0.1 工作台 · v0.2 Harness · v1.0 Agentic OS  
- ⏳ develop：清空业务到 legacy，按 ADR-016 重建
