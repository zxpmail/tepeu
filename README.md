# Tepeu

**2026-09-06**：从零重写。规划见 [`docs/rewrite-0.md`](docs/rewrite-0.md)。新库根 `tepeu/`。标本在 [`legacy/os-9/`](./legacy/os-9/README.md)。

v1.0 工作台在 `main` / tag `v1.0.0`，标本在 [`legacy/`](./legacy/README.md)。

## 分支

| 分支 | 用途 |
|------|------|
| `main` | v1.0 冻结线 |
| `develop` | 重写与新内核（当前） |

## 仓库布局（develop）

```
docs/rewrite-0.md      从零重写规划（当前必读）
tepeu/                 新库根
legacy/os-9/           冻结标本
legacy/                v1 工作台
docs/archive/          参照与结构摘录
memory/                交接与 ADR 史
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

规划：[`docs/rewrite-0.md`](docs/rewrite-0.md)。阅读序：`CONTEXT.md` → 规划 → [`docs/tepeu-foundation.html`](docs/tepeu-foundation.html)。

| 文件 | 用途 |
|------|------|
| [CONTEXT.md](./CONTEXT.md) | 当前进度 |
| [docs/rewrite-0.md](docs/rewrite-0.md) | 从零重写规划 |
| [docs/tepeu-foundation.html](docs/tepeu-foundation.html) | 结构说明 |
| [Product-Spec.md](./Product-Spec.md) | v1 产品规格档案 |
| [DEV-PLAN.md](./DEV-PLAN.md) | v1 交付切片档案 |
| [legacy/README.md](./legacy/README.md) | v1 代码说明 |
| [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md) | v1.0.0 说明 |

## 已完成（v1 摘要）

- v0.1 工作台 · v0.2 Harness · v1.0 产品里程碑
- develop：从零重写；第一刀进行中（identity 待人审）
