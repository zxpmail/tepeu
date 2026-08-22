# tepeu 距 Agent OS 还差什么

> **地位**：诚实度对照。规范 ADR-016 + [`os-baseplate.md`](./os-baseplate.md)；独有章 [`os-handbook.md`](./os-handbook.md)。v1 规格不是上级文档。  
> **日期**：2026-08-18。

---

## 0. 总判

按 tepeu **自己的定义**（不变量进内核、能力在缝上、门上四样焊死）：

**当前 = kernel 切片 + llm(fake) + Loop 答复路径 + 工具循环**，**不是** Agent OS。

`os/README` 身份陈述是**目标态文案**；实现进度见该文件状态表。底板 §8 已钉：**不许靠 OS 类比暗示已具备完整 OS。**

---

## 1. 现在有什么（打折读）

| 已有 | 打折后 |
|------|--------|
| ① 内核端口（identity / session / bus / policy… 已拆成组件模块） | 进程内契约；≠ 完整会话生命周期产品 |
| C1/C2/C3 审批与 fail-closed（第九轮） | 门地板；≠ 规则集做真。完成门在 Loop（答复主路 + 工具成对） |
| 三 store / Metering 端口 + 内存实现 | 端口有；持久化 barrier / 预算真实往返未证伪 |
| conformance 绿 | 测已声明契约；不证明「能跑 Agent」 |
| 第十轮 LlmProvider / 派生式断言 | **fake 路径已落码**（`os/llm`）；真 HTTP 未做 |
| ③ Loop 答复 + 工具循环 | **claim → generate →（可选 syscall 工具）→ 完成门**；maintenance / DoomLoop 未做 |

**未有**：真 HTTP `llm` 传输、真 `execution.*`/沙箱、⑤ 应用面。`compose` 仅内存接线 + fake llm + Loop。

---

## 2. 最少五层（可称「本机 Agent OS 骨架可演示」）

五层均须 **落码 + 可证伪**（conformance 或真实往返）。前面未过，禁止称骨架可演示。

| 序 | 层 | 缺什么 | 缺了为什么不算 OS | 主归属 |
|----|-----|--------|-------------------|--------|
| **1** | **进模诚实** | 真 HTTP 传输；未知用量/价格仍 n/a | fake 可证伪契约，不能证伪真实模型通道 | llm 真 HTTP（一族一刀） |
| **2** | **控制循环** | maintenance、DoomLoop | 答复+工具可证伪；还不是完整调度内核 | ③ Loop |
| **3** | **完成权** | 文件 locator / PLAN_STEP 门进主路 | 答复+工具成对门已有；主路完成仍是 REPLY | ③ Loop |
| **4** | **执行缝** | `execution.*` + 沙箱（隔离完备性如实报告）；工具经总线；先落日志再执行 | Policy=授权 ≠ 隔离；碰真实世界无边界 | Execution / Tool 契约 |
| **5** | **预算/审批做真** | 超限不得 claim/开跑；默认规则矩阵 + 同 turn 可回放 | 端口有 ≠ 门在往返里咬住 | Metering+Policy；审批规则集 |

**口径**：五层钉死后 → 可说「**本机 Agent OS 骨架可演示**」。  
仍**不得**称「企业 Agent OS 已成形」或「完整 OS」。

---

## 3. 骨架可演示之后仍欠的（成色债）

底板 §8 + §8.5 是显式债务与挂账的**权威清单**（调度公平 / Agent 资源隔离 / 运行中能力撤销 / tamper-evidence / 投影 ACL / ledger barrier / 多副本 fencing / append-only vs 删除权待裁决），**不是**下一刀必做清单，但缺则不得暗示已具备——不在此复读，下表只列底板未覆盖的增量：

| 债 | 说明 |
|----|------|
| 记忆平面 | **无则产品话术闭嘴**（P0-b 保护；源自 [`work-docs-absorption.md`](./work-docs-absorption.md) §2.4） |
| ⑤ UI / compose 接线 | 非内核，但是可演示产品面 |

---

## 4. 与切片顺序对齐

```text
现在 ──► llm.* fake（层1 契约）──► ③ Loop 答复+工具（层2 半截 / 层3 半截）──► 预算/审批往返（层5）
                              └─► llm 真 HTTP（层1 通道） / maintenance / execution/沙箱（层4）
         ──► 成色债按痛点排，禁止用「对齐外部」插队
```

下一刀（CONTEXT）：llm 真 HTTP（一族一刀），或预算硬门进往返，或 maintenance。禁止称骨架可演示。

---

## 5. 禁止口径（评审否决语）

- 「内核端口齐了 / 对账多轮了 → OS 成形」  
- 「conformance 全绿 → 能跑 Agent」  
- 「v1.0 已发布 → os/ 成熟」（legacy 标本，非 os/）  
- 「Product-Spec §1.1 / README 旧完成栏 → 完整 OS 已交付」  
- 「有审批端口 → 完成证据门已过」  
- 五层未钉死时对外使用「Agent OS 已可演示 / 已成形」

---

## 6. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-08-18 | 初版：总判 + 五层最低条 + 成色债 + 禁止口径 |
| 2026-08-18 | 口径：Product-Spec = v1 产品圣经；禁止用规格 §1.1 宣称 OS 已交付 |
| 2026-08-18 | 挂 [`os-handbook.md`](./os-handbook.md)（缺章投影，不开新裁决） |
| 2026-08-22 | Loop 答复路径落码：claim/完成门可离线证伪；工具循环仍缺 |
| 2026-08-22 | Loop 工具循环：先落 CALL 再总线执行；拦截合成 RESULT；maintenance 仍缺 |
