# spaceXP × 结构标本

> **地位**：结构口味，不是内核参照。不进底板，不新开 ADR / jar。  
> **来源**：[zxpmail/spaceXP](https://github.com/zxpmail/spaceXP)（GPL-3.0；Spring Boot 2.7 / 3.2 积木 starter）。2026-09-02 读 README + `spaceXP-framework` 目录，未 clone、未跑。  
> **一句话**：有些实现不优雅，但找文件不用猜。结构清楚才能长期跑；糊了就是泥球，只能日抛。

## 它靠什么清晰

```text
spaceXP-dependencies     BOM
spaceXP-framework        一件能力一个 starter
spaceXP-tests            一个 starter 一份对齐测试
spaceXP-tools            网关 / 生成器 — 不是 framework
```

starter 内部角色几乎同一套：`annotation` / `autoconfigure` / `config` / `core` / `model` / `properties`。每个模块 README 只说这一件事。`spaceXP-common` 是跨模块词汇（异常、ApiResult），不是业务口袋。

## 和 `os/` 的对应

| spaceXP | Tepeu | 吸不吸 |
|---------|--------|--------|
| 一件事一个 jar | `os/` 一组件一模块 | **已有。要对齐的是组件内角色，不是再切 jar** |
| 模块内固定角色分包 | 端口在根包；适配在 `.persist`；夹具在 `src/test` | **吸这套角色，不要吸 `autoconfigure`/`utils` 第四套** |
| 模块 README 一句能力 | `os/session/README.md` 已是这个写法 | 新模块照写 |
| `common` 词汇 | `identity` / `syscall` | 已有。禁止把 common 做成工具箱 |
| `tools` 应用 | `host/` | 已有。不进 `os/` |
| `tests` 按模块对齐 | 各组件 `src/test` + `conformance` | 已有 |
| Spring AutoConfig / AOP / Redis / MP | — | **不吸** |

## 反面（他们有、我们不跟）

- `spaceXP-common` 里一长串 `*Utils` — 词汇层禁止再堆工具
- 为 Spring 插头准备的 `autoconfigure` / `spring.factories` — `os/` 零 Boot
- 单文件文笔、复制式实现 — 不作为审核标准

不因本文改 `os/`、不重切 session 根包。下一刀新文件按 `.forge/project-taste.md` 的角色档放。
