# 纹理 — 盒子与缝

> **人给的口径（2026-09-05）**：像 spaceXP，好理解优先。一件事一个盒子，README 一句人话。要第二句才能解释「为什么单独存在」的，并掉。  
> **地位**：目标模块表。规划以 [`docs/rewrite-0.md`](../docs/rewrite-0.md) 为准。`os/` `host/` 冻结。  
> **结构说明**：[`docs/tepeu-foundation.html`](../docs/tepeu-foundation.html)

spaceXP 靠什么好懂：framework 一件能力一个模块；common 是共用类型；tools 是应用。

## 目标目录

```text
tepeu/
  types/
    identity/            共用类型：谁、哪个工作区、哪一次对话
    syscall/             共用类型：一次调用的名称、参数、结果
  persist/
    api/
    sqlite/              第一刀
  kernel/
    session/             一次对话存下来的状态
    policy/              授权：准 / 不准 / 先问你
    dispatch/            调用分发
  run/
    llm/                 网关统一调用；第一刀后面挂 fake
    execution/           工作区文件与进程
    loop/                控制循环
  commands/              斜杠命令表。/help /approve /status /btw
  load/                  装配：注入实现、拼系统提示、登记斜杠
  conformance/           契约测试套件
host/                    Spring Boot（无 Web）。CLI。`/` 行交给 commands
```

从零规划以 [`docs/rewrite-0.md`](../docs/rewrite-0.md) 为准。

## 表一 · 模块

| 名 | 干什么 |
|----|--------|
| session | 一次对话存下来的状态 |
| policy | 授权：准 / 不准 / 先问你 |
| persist | 父 POM：api + sqlite（第一刀）。host 依赖哪个用哪个 |
| dispatch | 调用分发 |
| llm | 网关统一调用。第一刀 fake 挂在网关后 |
| loop | 控制循环 |
| execution | 工作区文件与进程 |
| identity | 谁、工作区、哪一次对话。共用类型 |
| syscall | 一次调用的信封。共用类型 |
| commands | 斜杠命令表。/help /approve /status /btw |
| load | 装配：注入实现、拼系统提示、登记斜杠 |
| conformance | 契约测试套件 |
| host | Spring Boot（无 Web）。CLI。`/` 行交给 commands |

## 表二 · 缝和纹理

| 口 | 人站哪 | 怎么做 |
|----|--------|--------|
| 外来机制放哪 | 纹理 | 人点：进上图哪个盒 / 新盒 |
| 新盒子 | 纹理 | 人批。新盒子能写进上图那一行 |
| 工具出门：policy 问人 | **缝** | `/approve` |
| 何谓完 | 纹理 | 人点规则；机器按规则宣判 |
| schema / `completed` 第二出口 | 纹理 | 人批 |
| 看 CLI / 绿测 | on loop | 验证 |

## 表三 · 产品进路

核：事件日志 + 授权。下面是司机或读账口。

| # | 进路 | 是什么 | 现仓 | 下一刀 |
|---|------|--------|------|--------|
| 0 | 对话主路 | 打字 → Loop → 写 entries | 有 | 第一种司机 |
| 1 | Slash | 命令表。缝：`/approve`。读账：`/status`。旁问：`/btw` | `/help` `/approve` `/status` `/btw` | `/compact` `/tasks` |
| 2 | 人手旁路 | 直接 `dispatch.invoke`；事实进操作审计 | 提案 | 谁从哪敲：CLI 子命令 |
| 3 | 后台环 | 压缩等 idle 自己跑，也出门、也写账 | 有 | 经 `/status` 可见 |
| 4 | 任务执行 | 定时或长任务自己开工；命令能看状态 | 提案 | 状态放寄存器还是新表 |

看状态挂在 1。4 是第二种干活的司机。动 2 / 4 先问切口。

## 表四 · 切口待批

**进路 3 — 后台环可见**  
`cut_list: none`。`/status` 已有 `loop.state`；压缩落过 `COMPACTION_CHECKPOINT` 会出现在 entries 列表。

**进路 2 — 人手旁路（提案，等人圈）**

| # | 切口 | 安置 | 谁批 |
|---|------|------|------|
| 2a | 从哪敲 | **host CLI 子命令** `invoke <syscall> k=v` | 人 |
| 2b | 走哪 | `dispatch.invoke` | 复用 |
| 2c | 事实落哪 | 操作审计 | 复用 |
| 2d | 完成 | 完成判定仍在对话路径的单一判定点 | 纹理 |
| 2e | 实现放哪 | `host/` | — |

**进路 4 — 任务执行（提案，等人圈；与 2 分开批）**

| # | 切口 | 安置 | 谁批 |
|---|------|------|------|
| 4a | 第一刀 | **定时开一轮对话**：到点把固定句子推进 Inbox，再跑已有 Loop | 人 |
| 4b | 这算哪种司机 | 钟当扳机，干活仍是司机 0 | 人须认 |
| 4c | 状态存在哪 | 寄存器或新表。未圈 | 人 |
| 4d | 看状态 | `/tasks` 挂进路 1（Slash 读） | 复用 Slash |

## 表五 · 从头做 tepeu

人圈（2026-09-05）：**S0 认**。  
人圈（2026-09-06）：**S1 从零重写**。现行 `os/` `host/` 冻结，规划 [`docs/rewrite-0.md`](../docs/rewrite-0.md)。

**tepeu 是什么**  
本机一个人用。事件日志能复核。出门过 policy。司机可以有多条。人在纹理和门缝。

**第一天就能指给别人看的**  
host CLI：打字走司机 0；`/help` `/approve` `/status` `/btw` 走进路 1。

| # | 切口 | 提案 | 人圈 |
|---|------|------|------|
| S0 | 产品就是上两段 | 认 | **认** |
| S1 | 从零重写 | 规划 [`docs/rewrite-0.md`](../docs/rewrite-0.md) | **2026-09-06** |
| S2 | 下一刀写码 | 表四 **2a**（`invoke`），圈过才写 | |
| S3 | 进路 4 | 2 走过再谈 | |

## 表六 · 重写

重写 = 目录和走法变成表一。条文在 [`docs/rewrite-0.md`](../docs/rewrite-0.md)。

**完成时能看见**

1. 一张纸能画完：打字 / Slash / 旁路，出门经 dispatch，事实进 entries 或操作审计。
2. 跑一轮，`/status` 或打开库，对得上刚才的事。
3. 新文件进表一那些盒。
4. 外来机制人先点位置。

一次一个模块：写完、测完、install。圈定 §10 后写实现。
