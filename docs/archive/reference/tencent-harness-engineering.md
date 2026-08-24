# 腾讯 Harness Engineering（AI Coding）— 勿当 Tepeu 参照

> **地位**：存档备查，**不是**吸收参照，**不是**规范。  
> **来源**：[驾驭AI Coding：一份面向团队的Harness Engineering落地规范](https://mp.weixin.qq.com/s/g4nTfxm7ebzRwkAVIGdIbg)（腾讯技术工程 / atreusliu）。  
> **日期**：2026-08-24。  
> **结论（已复核）**：与 Tepeu `os/` **不在同一条线**——参照价值低；留下本文只为防混谈。

---

## 为什么参照不了

| | 腾讯这篇 | Tepeu develop |
|--|----------|----------------|
| 对象 | 人用 IDE/AI Coding 写业务代码 | 业务 Agent 的运行时 OS |
| Harness | Rules / Skills / MCP / AGENTS / Code Review 工程壳 | syscall · Policy · Loop · 三 store |
| 成功标准 | 团队交付「好代码」 | 不裸奔、可审计、可证伪内核契约 |

公式 `Agent = Model + Harness` 是业界口号；**落地物（CodeBuddy、Knot、3+1 Phase、team-harness 仓）对 `os/` 架构决策几乎无映射。**

勿用六支柱验收 Agent OS，勿开 `os/harness/` jar，勿把 Git 回滚当成运行中能力撤销。

---

## 若仍看到「六支柱」

那是 **AI Coding 产品检查表**，不是 Tepeu 组件清单。  
真正同线的参照仍是：底板 / ADR-016、[agent-runtime-security-series.md](./agent-runtime-security-series.md)（运行时安全）、Pi/CC/gnex3 等运行时对账。

**上下文管理 / Context 组件** 的讨论以底板与安全系列为准，**不要**从本文六支柱推导组件切分。

---

## 索引

| 文档 | 关系 |
|------|------|
| [os-handbook.md](../../os-handbook.md) | 术语 Harness = v1 能力壳 ≠ develop 内核 |
| [agent-os-gap.md](../../agent-os-gap.md) | 成色债；勿用外部 Coding 规范插队 |

**不**因本文新增 ADR；**不**列入吸收清单的「有利吸取」对象。
