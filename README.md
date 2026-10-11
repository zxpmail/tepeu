# Tepeu

**2026-10-02**：现行规则在 [`docs/rewrite-1.md`](docs/rewrite-1.md)，用平常话说明这一轮怎么管。程序在 [`tepeu/runtime`](./tepeu/runtime)。旧程序在 [`legacy/first-knife/`](./legacy/first-knife/README.md)，只读。旧规划 [`docs/rewrite-0.md`](docs/rewrite-0.md) 留着不续。更早的标本在 [`legacy/os-9/`](./legacy/os-9/README.md)。

v1.0 工作台在 `main` / tag `v1.0.0`，标本在 [`legacy/`](./legacy/README.md)。

## 分支

| 分支 | 用途 |
|------|------|
| `main` | v1.0 冻结线 |
| `develop` | 重写与新内核（当前） |

## 仓库布局（develop）

```
docs/rewrite-1.md      这一轮怎么管（当前必读）
docs/specs/          对外一道门，加上七篇：一轮、账、回答、检查口、做事、给模型看、判断
tepeu/runtime         这一轮的程序。一个模块，七个包，外面一道门
tepeu/cli             命令行。Spring Boot，没有网页。跑一轮，打出两本记录
legacy/first-knife/    第一刀整套程序（只读）
docs/rewrite-0.md      第一刀旧规划（保留）
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

## 这一轮的命令行

没有网页。不带参数就停在那里，你说一句，它答一句。说「停」或 stop 结束。这一次最多问 32 次。供应商是本机 cc-switch 里的 Xiaomi MiMo。

带上三个字就只跑一轮：要做什么、限制、怎样算做完。

在含有 `runtime` 和 `cli` 的目录里执行，也就是仓库里的 `tepeu/tepeu`。不要先进入 `cli`。

```bash
cd tepeu/tepeu
mvn -pl cli -am package
java -jar cli/target/tepeu-cli.jar
```

## 这一轮怎么测

```bash
cd tepeu/tepeu
mvn -pl cli -am test
```

## 文档入口

规划：[`docs/rewrite-1.md`](docs/rewrite-1.md)。阅读序：`CONTEXT.md` → `memory/handoff.md` → 规划。

| 文件 | 用途 |
|------|------|
| [CONTEXT.md](./CONTEXT.md) | 当前进度 |
| [docs/rewrite-1.md](docs/rewrite-1.md) | 这一轮怎么管 |
| [docs/rewrite-0.md](docs/rewrite-0.md) | 旧规划，留着不续 |
| [docs/tepeu-foundation.html](docs/tepeu-foundation.html) | 结构说明 |
| [Product-Spec.md](./Product-Spec.md) | v1 产品规格档案 |
| [DEV-PLAN.md](./DEV-PLAN.md) | v1 交付切片档案 |
| [legacy/README.md](./legacy/README.md) | v1 代码说明 |
| [RELEASE_NOTES-v1.0.0.md](./RELEASE_NOTES-v1.0.0.md) | v1.0.0 说明 |

## 已完成（v1 摘要）

- v0.1 工作台 · v0.2 Harness · v1.0 产品里程碑
- develop：这一轮的程序在 `tepeu/runtime`。第一刀已归档，只读
