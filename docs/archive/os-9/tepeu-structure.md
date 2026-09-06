# Tepeu 结构一页（os-9 标本）

> **档案。** 2026-09-06 现网九组件照片。不是规范，不要按这份开新盒。  
> 对标：v1 记忆在 [`../v1/`](../v1/)。本目录说明见 [README](./README.md)。  
> 目标七盒：[`memory/texture.md`](../../../memory/texture.md) 表一。细则：[`os-baseplate.md`](../../os-baseplate.md) / ADR-016。  
> **产品**（当时已圈）：本机一个人用。核不知 turn：账 + 门。能指给人看的是 `host/` CLI。

| 现网 | 数 | 是什么 |
|------|----|--------|
| 组件 | **9** | 一件事一个 Maven 模块，能单独测、单独装 |
| 词 | **2** | 类型，没有运行时 |
| 测试夹 | **1** | conformance，不进发行路径 |
| 应用 | **1** | `host/`，在 `os/` 外 |
| 进程 | **1** | 一个 JVM。没有微服务、没有第二棵树 |

目标（人圈「像 XP」，未收完）：组件 **7**。`orchestration` / `compose` 要第二句，待并。`observation` 已并进 `llm`。

---

## 01 名称与分层

```text
┌─────────────────────────────────────────────────────────────┐
│ 应用  host/     给人用：接线入口 · 终端 · 斜杠 · /approve     │
├─────────────────────────────────────────────────────────────┤
│ 司机  loop/     跑一轮：读账 → 问模型 → 用工具 → 写账         │
│       orchestration/   现网残留：拼系统提示 + 斜杠登记        │
├─────────────────────────────────────────────────────────────┤
│ 门    bus/      点名去调一项能力，出门先过 policy             │
│       policy/   能不能跑：行 / 不行 / 问你                    │
├─────────────────────────────────────────────────────────────┤
│ 账    session/  说过什么。打开库能复核                        │
│ 库    persist/  怎么读写。不管会话长什么样                    │
├─────────────────────────────────────────────────────────────┤
│ 插头  llm/      跟模型说话（给它看什么、发出去、收回来）      │
│       execution/ 读文件、写文件、跑进程，关在工作区里         │
├─────────────────────────────────────────────────────────────┤
│ 接线  compose/  现网残留：把插头插上（XP 的 tools，不是能力） │
├─────────────────────────────────────────────────────────────┤
│ 词    identity/ 谁、哪次会话     syscall/ 一次调用的信封      │
│ 测    conformance/ 一个盒子一份对齐测试                       │
└─────────────────────────────────────────────────────────────┘

世界：LLM API · 本机文件/进程 · SQLite
```

核 = **账 + 门**（session + policy，经 bus 出门）。Loop 是司机，不是核。host 是应用，不是组件。

---

## 02 组件目录（现网 9）

每行：一句人话 · 提供 · 不做什么 · 单独测。

| # | 名 | 一句人话 | 提供 | 不做 | 测 |
|---|----|----------|------|------|----|
| 1 | session | 这一轮说过什么 | entries / registers / ledger · Inbox/claim · AuditSink · Metering 口 | 不判能不能跑、不跑 turn、不建连接 | `-pl session -am test` |
| 2 | policy | 这个工具这次能不能跑 | allow / deny / ask · 审批单 | 不调模型、不写对话事实 | `-pl policy -am test` |
| 3 | persist | 怎么读写库 | `Persist` 访问口 · sqlite JDBC | 不管会话长什么样、无领域类型 | `os/persist` |
| 4 | bus | 点名去调一项能力 | 注册/分发 · 出门先 Policy+卫兵 | 不承载业务 | `-pl bus -am test` |
| 5 | llm | 跟模型说话 | `Observation.view`（看什么）· 传输 · `llm.generate` 插头 | 不拼产品系统提示（现仍在 orchestration） | `-pl llm -am test` |
| 6 | loop | 跑一轮 | claim → 有界 turn → 工具 → 完成门 · 压缩/维护窗 | 不 import 具体 Tool 类 | `-pl loop -am test` |
| 7 | execution | 文件和进程关在工作区 | `fs.*` / `proc.spawn` · 囚笼（隔离 partial） | 不判授权（那是 policy） | `-pl execution -am test` |
| 8 | orchestration | 拼提示 + 斜杠 | PromptAssembly · CommandDispatcher | **要第二句。** 目标：拼装→llm，斜杠→host | `-pl orchestration -am test` |
| 9 | compose | 把插头插上 | Memory/Sqlite 接线 · 注册 handler 与 `/help` `/approve` `/status` | **要第二句。** 目标：并 host | `-pl compose -am test` |

**不是组件**

| 名 | 角色 |
|----|------|
| identity | 词：谁 / 在哪 / 哪次会话 / TurnContext |
| syscall | 词：调用信封 + Usage |
| conformance | 测试夹，test scope |
| host | 应用。建连接、读密钥、跑 CLI。依赖 compose |

尚未落码、**不是**现网组件：Team / Subagent / LongTask / 路由三决策。不要在目录里占空位。

---

## 03 怎么运行（一个进程，四条进路）

开机（host）：建 SQLite 连接 → 包成 `Persist` → compose 接线 → 注册 `llm.*` / `execution.*` → 挂 `/help` `/approve` `/status` → 等人打字。

出门一律：`bus.invoke` → Policy+卫兵 → 已注册插头。没有第二条门。

| # | 进路 | 走法 | 事实落哪 | 现网 |
|---|------|------|----------|------|
| 0 | 对话主路 | 打字 → Inbox → **loop** 读账 → llm 裁可见面 →（orchestration 拼提示）→ `llm.generate` → 模型要工具则再出门 → 写 entries → CompletionGate | **entries**（用量 **ledger**） | 有 |
| 1 | Slash | `/` → CommandDispatcher，**不经模型**。`/approve` 是门缝；`/status` 读账 | 宿主副作用 → **AuditSink** | `/help` `/approve` `/status` |
| 2 | 人手旁路 | 跳过 loop，直接 `bus.invoke` | **AuditSink**，不写 entries | **无**（切口 2a 未圈） |
| 3 | 后台环 | idle 时 loop 自己压缩等 | 也出门、也写账（如 `COMPACTION_CHECKPOINT`） | 有，人从 `/status` 的 entries 看见 |
| 4 | 任务执行 | 定时或长任务自己开工 | 未圈 | **无**。无表则不写 `/tasks` |

完成：证据只在 entries，宣判只许 `CompletionGate`。工具回报、模型声称、CLI 退出码都不是终态。

---

## 04 组件间关系

### 运行时（谁调谁）

```text
host
 └─ compose          只接线，开机一次
      ├─ session     账
      ├─ persist     库（session/policy 适配器只认 Persist）
      ├─ policy      门
      ├─ bus         出门
      │    ├─► policy          先问
      │    ├─► llm 插头        llm.generate
      │    └─► execution 插头  fs.* / proc.spawn
      ├─ loop        司机 0    读 session，出门走 bus（不依赖 orchestration）
      ├─ llm         裁可见面 + 传输
      ├─ orchestration  拼提示；斜杠登记在此，命令类现放 compose
      └─ execution   囚笼实现，经 bus 被叫，不被 loop 直 import
```

硬关系（不是依赖图上的箭头，是纪律）：

- 碰能力 → 只经 **bus**。没有旁路直调插头。
- **loop 不 import** 具体 Tool 类；工具互引禁止。
- session / policy **不建连接、不关库**。host 建，compose 接。
- 模型看见的那一截只经 **llm `Observation.view`**。entries 不动。
- 人手事实进 **AuditSink**，不进 entries。

### 编译依赖（Maven，省略 test / conformance）

```text
identity          （无 os 依赖）
syscall           → identity
persist           （无领域类型）
persist-sqlite    → persist
session           → identity, syscall, persist, persist-sqlite
policy            → identity, syscall, persist, persist-sqlite
bus               → identity, syscall, policy
llm               → identity, syscall, session
execution         → identity, syscall
loop              → identity, syscall, session, policy, bus
orchestration     → identity, session
compose           → 上面都会碰到
host              → compose
```

内不依赖外：session/policy/persist 不知道 loop、host、orchestration。  
loop 知道 session 和 bus，不知道 orchestration、execution 的类。  
compose / host 可以看见所有插头，因为它们是接线和应用。

---

## 05 一张纸验收

打开本页应能回答，不用翻 ADR：

1. 几个盒子、各叫什么、哪两个不是能力。
2. 打字、Slash、旁路各走哪，出门是否只经门。
3. 事实进 entries 还是 AuditSink。
4. 新文件该进哪一盒；进不了表一的名字不要开。

现仓还答不利落的：`orchestration`、`compose` 仍占盒。并盒按 `texture.md` 表六，一次一个，测绿再装。
