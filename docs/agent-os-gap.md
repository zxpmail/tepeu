# tepeu 距 Agent OS 还差什么

> **地位**：诚实度对照。规范 ADR-016 + [`os-baseplate.md`](./os-baseplate.md)；独有章 [`os-handbook.md`](./os-handbook.md)。v1 规格不是上级文档。  
> **日期**：2026-08-23。

---

## 0. 总判

按 tepeu **自己的定义**（不变量进内核、能力在缝上、门上四样焊死）：

**当前 = 本机 Agent OS 骨架可演示**（五层落码 + 可证伪）。**不是**企业 Agent OS，**不是**完整 OS。

`os/README` 身份陈述是**目标态文案**；实现进度见该文件状态表。底板 §8 已钉：**不许靠 OS 类比暗示已具备完整 OS。**

---

## 1. 现在有什么（打折读）

| 已有 | 打折后 |
|------|--------|
| ① 内核端口（identity / session / bus / policy… 已拆成组件模块） | 本机单写者 SQLite 发行件（fork/卫兵 verdict/AuditSink/recover/CAS）；≠ 完整会话生命周期产品 / 多副本 |
| C1/C2/C3 审批与 fail-closed（第九轮） | 门地板 + SQLite ApprovalStore + DefaultRuleMatrix + `/approve` + `policy.rules`；完成门在 Loop（REPLY + 推断 TOOL_PAIR / PLAN / FILE） |
| 三 store / Metering 端口 + SQLite WAL | 端口有；开 turn 预算门已咬；单写者 ledger fail-closed + read-your-writes 已测；多副本 barrier 未做 |
| conformance 绿 | 测已声明契约；不证明「能跑带 UI 的 Agent 产品」 |
| 第十轮 LlmProvider / 派生式断言 | **fake 已落码**；**Anthropic + OpenAI HTTP 薄壳已落**；live 测试 opt-in（无 key skip）；cost 仍 n/a |
| ③ Loop 答复 + 工具循环 | **claim →（overflow 压缩）→ generate →（syscall 工具 / plan / DoomLoop）→ 完成门**；`maintain` 窗 + `CompactionWork` |
| ③ PromptAssembly / Command | 静/动分离 + 超预算账单；Slash local/prompt + `/approve`。Team / 路由未落 |
| DefaultRuleMatrix | llm.* ALLOW；写盘/进程 ASK；未知 DENY。可覆盖。同 turn 回放 = C1 consume 一次 |
| execution.* | 工作区路径囚笼 + Job Object / bwrap；probe 报 **partial**；无 jail 时 spawn 失败可见。≠ landlock / 完整沙箱 |

**未有**：⑤ UI、记忆平面、多副本 fencing。`SqliteAssembly` 的 llm 默认仍 fake（传输由调用方注入）。

---

## 2. 最少五层（可称「本机 Agent OS 骨架可演示」）

五层均须 **落码 + 可证伪**（conformance 或真实往返）。前面未过，禁止称骨架可演示。

| 序 | 层 | 现状 | 主归属 |
|----|-----|------|--------|
| **1** | **进模诚实** | HTTP 薄壳 + `LlmTransports.fromEnv`；live 测试有 key 才烧；compose 不读密钥；cost = n/a | live 往返 |
| **2** | **控制循环** | turn 内 overflow 压缩（默认阈 40）+ maintain 作业；递减停机 = keepLast / 全 checkpoint，不是模型质量 | ③ Loop / Compaction |
| **3** | **完成权** | 主路 REPLY 必过；推断 TOOL_PAIR / PLAN / FILE（locator ∈ ContentStore） | ③ Loop |
| **4** | **执行缝** | Job Object / bwrap；隔离仍 **partial**（禁止报 FULL） | Execution |
| **5** | **预算/审批做真** | 开 turn 预算门 + 默认矩阵 + `/approve` + `policy.rules` | Policy / Loop |

**口径**：五层已钉 → 可说「**本机 Agent OS 骨架可演示**」。  
仍**不得**称「企业 Agent OS 已成形」或「完整 OS」。

---

## 3. 骨架可演示之后仍欠的（成色债）

底板 §8 + §8.5 是显式债务与挂账的**权威清单**（调度公平 / Agent 资源隔离 / 运行中能力撤销 / tamper-evidence / 投影 ACL / ledger barrier / 多副本 fencing / append-only vs 删除权待裁决），**不是**下一刀必做清单，但缺则不得暗示已具备——不在此复读，下表只列底板未覆盖的增量：

| 债 | 说明 |
|----|------|
| 记忆平面 | **无则产品话术闭嘴**（P0-b 保护；源自 [`work-docs-absorption.md`](./work-docs-absorption.md) §2.4） |
| ⑤ UI / 产品面 | 非内核；骨架可演示 ≠ 可给非开发者用的工作台 |
| live CI | 无 key 不烧；有钥匙才是真往返 |

---

## 4. 与切片顺序对齐

```text
现在 ──► 本机 Agent OS 骨架可演示
         ──► 成色债按痛点排（UI / 记忆 / fencing），禁止用「对齐外部」插队
```

下一刀按痛点，不按「再对账一轮」。

---

## 5. 禁止口径（评审否决语）

- 「内核端口齐了 / 对账多轮了 → OS 成形」  
- 「conformance 全绿 → 能跑带 UI 的 Agent 产品」  
- 「v1.0 已发布 → os/ 成熟」（legacy 标本，非 os/）  
- 「Product-Spec §1.1 / README 旧完成栏 → 完整 OS 已交付」  
- 「有审批端口 → 完成证据门已过」  
- 「骨架可演示 → 企业 Agent OS / 完整 OS / isolation=full」

---

## 6. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-08-18 | 初版：总判 + 五层最低条 + 成色债 + 禁止口径 |
| 2026-08-18 | 口径：Product-Spec = v1 产品圣经；禁止用规格 §1.1 宣称 OS 已交付 |
| 2026-08-18 | 挂 [`os-handbook.md`](./os-handbook.md)（缺章投影，不开新裁决） |
| 2026-08-22 | Loop 答复路径落码：claim/完成门可离线证伪；工具循环仍缺 |
| 2026-08-22 | Loop 工具循环：先落 CALL 再总线执行；拦截合成 RESULT；maintenance 仍缺 |
| 2026-08-22 | Anthropic HTTP 薄壳：JDK HttpClient，body≡prepare，离线 stub；OpenAI/live 未做 |
| 2026-08-23 | OpenAI HTTP 薄壳：Bearer + `/v1/chat/completions`，body≡prepare；prompt_tokens 含缓存须拆；live 未做 |
| 2026-08-23 | 预算硬门进往返：LedgerMetering token 硬顶；Loop claim 前 STOPPED；compose 默认 unlimited |
| 2026-08-23 | Loop maintenance 窗 + DoomLoop：独占窗/上限/NOW 让位/latch；连续 3 次熔断 + NUDGE 入 RESULT |
| 2026-08-23 | PromptAssembly + CommandDispatcher：orchestration 开成第 7 组件；静/动分离、超预算账单；Slash 不经模型 |
| 2026-08-23 | 内核契约收口：fork+END_SEED、卫兵 deny>ask>allow、AuditSink、recover、sha256 CAS；SQLite 仍未写 |
| 2026-08-23 | 本机单写者内核可发行：SQLite WAL schema v1 + SqliteAssembly；MemoryAssembly 仅测试；仍非 OS |
| 2026-08-23 | Compaction 挂窗 + DefaultRuleMatrix + execution 囚笼（partial）；仍非骨架可演示 |
| 2026-08-23 | 五层钉死：live opt-in、overflow 压缩、PLAN/FILE 门、Job Object/bwrap、/approve；口径=骨架可演示 |
| 2026-08-23 | 审查修补：surfaceEpoch、CREATE_SUSPENDED、env 白名单、argsDigest、jail NOFOLLOW、/approve 会话校验 |
