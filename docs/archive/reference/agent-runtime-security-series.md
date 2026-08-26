# Agent 运行时安全系列 × Tepeu 对账（中肯评价）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../../../memory/decisions-log.md) + [os-baseplate.md](../../os-baseplate.md) 为准；诚实度见 [agent-os-gap.md](../../agent-os-gap.md)。  
> **来源**：外部「Agent 运行时安全」工具书系列（开篇 + 工具防控第 2–4 篇）。正文由会话粘贴；微信原文链接可能需环境验证。  
> **日期**：2026-08-24。评价已复核：主判断站得住；文中「略满」处已收紧。

**总纪律（四篇共用）**

- 九宫格是**产品/治理坐标系**，不是再开一层 `security/` 内核，更不是九个 Maven jar。
- 对外可叫 ToolGate / 处置中心；对内落在 **syscall 总线入口：取消 → 卫兵 → Policy → 分发**，复用 `deny > ask > allow`、fail-closed、审批单次。
- **Gate = 边界上执行脚本**；脚本意图（安全拦截 / 教学调难度 / 别的）是配置面，不是架构原语。
- **观测可改、verifier 不可改**——对生产同样成立（脱敏、截断、合成模型可见结果都是观测面；完成门 / 审批证据 / 已落账真相不动）。
- **Observation 是组件**（模型可见视图管道）：`os/observation`，入口 `Observation.view`。PromptAssembly 仍独立。gate/response **空 jar 不预开**。
- **空 jar 不预开**：先长能力 + conformance，再考虑拆 `gate/` / `response/`。
- 显式债务（运行中能力撤销、资源隔离边界、tamper-evidence）见底板 §8 / gap——**是债不是排期**。

---

## 0. 开篇：三阶段 × 三模块九宫格

**文章**：《Agent运行时安全全景图：三阶段×三模块，把自主Agent关进笼子》

**中肯结论**：地图立得住；对 Tepeu 是坐标系，不是新内核层。

| 主张 | 对 Tepeu |
|------|----------|
| Agent ≠ ChatBot：管「做」不（只）管「说」 | 与「syscall 过 Policy」同构 |
| 推理与执行同进程 = 裸奔 RCE 面 | Policy≠Sandbox；隔离在 spawn 点 |
| 单点白名单必被组合绕过 | 接第 3 篇 Gate / 归一化后再 Policy |
| 三阶段 × 三模块正交 | 可排期；比堆原则有用 |
| P0 先灭裸奔 | 与「先粗糙基线」同序 |
| 审计是溯源地基 | entries + AuditSink 双真相域 |

**叠到 Tepeu（诚实版）**

```
                 应用前              使用中                 识别后
工具    Policy规则+syscall登记    总线卫兵+Policy(+Gate)   处置链（挂账）
Skill   资产审查（⑤/Prompt）      沙箱+动态授权（弱）       隔离+信誉（未）
MCP     Server基线（②未厚）       实时审计（部分）          断会话/换凭据（债）
```

- **工具行**大半对齐；**Skill/MCP 两行**多为挂账/未厚——勿读成「九格都齐」。
- 已对齐：`deny>ask>allow`、fail-closed、审批单次、子代理只减不增、DoomLoop 雏形、execution **partial**。
- 运营清单里 P3 事件响应可理解；ADR「运行中撤销」是**架构债**，别与运营优先级混成「永远最后做」。

**打折**：国家背景黑客百分比当开场即可，不作架构依据；opencode/OpenClaw 绑定是工具书好，正典仍是 syscall 名族 + Policy + Sandbox。

---

## 1. 第 2 篇：工具注册与白名单基线（应用前 · 工具）

**文章**：《工具注册与白名单基线：Agent动手之前，先把边界画好》

**中肯结论**：系列地基，方向对；大半已写入 ADR，缺的是**登记粒度**与**参数级白名单**，不是再发明 `gate.py`。

| 文章主张 | Tepeu |
|----------|--------|
| Agent 无权限，权限 = 工具并集 | Policy 判 **syscall**，不是 Agent 角色 |
| 默认拒绝 | `DefaultRuleMatrix` 未知 DENY；未装配 Policy = fail-closed |
| deny > ask > allow | 卫兵代数；「批准压不过拒绝」 |
| 审批单次 / 超时默认拒 | `ApprovalStore` 严格单次 |
| 子代理独立边界 | 对父有效集只减不增 + `delegationDepth` 单调下界 |
| 先粗糙再精细 | 与「先别干不可逆坏事」同序 |

**映射（勿另开注册表产品）**

1. **存在性注册** = 总线 syscall 表（已有；规范序已落）
2. **授权登记** = Policy 规则面（`policy.rules` / 矩阵）+ 上线资产清单（owner、risk_tier、MCP/Skill 源引用）——后者是运维/合规资产，**不进①内核**

`gate.py` ≡ 总线入口的 Policy +（日后）参数卫兵，不是旁路脚本。四分类（builtin/skill/mcp/subagent）对齐命名族与缝，不必进内核 type enum。

**打折**

- 户口字段过胖：命令/路径/egress 混一张表 → 应拆 Policy（授权）与 Sandbox/egress（隔离）
- opencode 字符串白名单演示可用，生产要归一化执行单元（文章自述 OpenClaw 词法坑）
- L1/L2/L3 是产品决策树；内核只要 `allow|deny|ask`
- 现状：`DefaultRuleMatrix` 仍是 **syscall 名级**；参数级与「工具集版本化快照锁存」仍挂账

---

## 2. 第 3 篇：ToolGate 实时拦截（使用中 · 工具）

**文章**：《实时拦截引擎 ToolGate：在Agent抬手那瞬间把它按回去》

**中肯结论**：方向对；**现在就开 `os/gate/` 偏早**——先长四闸门能力 + conformance，再拆模块。

| 文中 ToolGate | Tepeu 已有 / 缺口 |
|---------------|-------------------|
| Brain→Hands 中间件 | 总线：卫兵 → Policy → 分发 |
| 敏感 → 人审 | `NEED_APPROVAL` + `ApprovalStore` |
| 行为熔断 | DoomLoop：熔断+NUDGE 已落；第三刀改 NEED_APPROVAL 仍挂账 |
| 拦序列非单字符串 | **缺口**：命令归一化后再 Policy；跨工具序列指纹 |

**组件口径**

- Gate 应是 Policy / Approval / execution 的**装配者**，不是第二套 Policy。
- 总线顺序不变：`取消 → 卫兵(gate) → Policy → 分发`。
- 双头风险：Gate 若另裁决且不走 deny>ask>allow + ApprovalStore，比没有更危险。

**节奏**

| 阶段 | 动作 |
|------|------|
| 现在 | 补归一化、序列熔断、DoomLoop→ask；证据落 entries/审批 |
| 再拆 | 可选 `os/gate/`（一件事可单独测） |
| 永不 | 空 jar；绕过总线的 Python 中间件当正典 |

---

## 3. 第 4 篇：识别后处置链（识别后 · 工具）

**文章**：阻断 → 撤销 → 隔离 → 溯源（工具防控三期收官）

**中肯结论**：比 ToolGate **更贴已写明的 ADR 缺口**（叙事贴合度，≠下一刀必做）；「一键脚本」不能当内核；处置是**跨组件编排**，不是空开 `revoke/` / `response/`。

| 文章主张 | Tepeu 现状 |
|----------|------------|
| 拦截只救当下 | 卫兵 + `STOPPED`；缺完整「识别后链」 |
| 阻断→撤销→隔离→溯源 | 显式债：运行中能力撤销、隔离边界不全 |
| 先停手再取证 | Loop 取消 + 合成 TOOL_RESULT ≠ 杀进程/断 egress |
| 三层时间线 | entries + AuditSink + ledger；**未**拼成 Timeline 产品 |
| 静态长 token 无法回收 | Secret/IAM/上线登记——内核外地基债 |

**分工示意**

```
异常信号 → 编排（停 Loop / principal deny / 锁窗）
         → Policy/Approval 不再发放
         → execution 收口（partial 诚实）
         → 场外：Secret/IAM revoke、egress、channel
         → entries + AuditSink → Timeline
```

**打折 / 复核收紧**

- 「处置态」是设计建议；现状只有 `idle|running|maintenance` + turn `STOPPED`
- `STOPPED`/取消有；凭据当场作废与三层时间线**答不全**——正是债
- 勿读成「马上开 `response/`」

**建议落码序（若动手）**：principal deny + 不可 claim 的 conformance 演练 → Timeline 从 entries 拼 → 再考虑是否拆编排模块。

---

## 4. 工具三格合读（第 2–4 篇）

```
第2篇 登记+默认拒绝  → Policy 规则面 + syscall 存在性
第3篇 抬手拦截       → 解析/序列卫兵 → 再喂 Policy
第4篇 识别后止血     → 停手 / 撤证 / 隔离 / 溯源编排
```

Skill / MCP 六格未进主线排期时，底线仍是：**不得从旁路绕过总线**。

---

## 5. 禁止读法（防误吸）

| 误读 | 正确读法 |
|------|----------|
| 九格 = 九个组件 jar | 坐标系；工具三格 ≈ Policy + 卫兵/Gate + Response 编排 |
| 立刻开 `gate/` / `response/` | 先能力与 conformance |
| 抄 opencode.json / revoke_agent.py 进内核 | 映射到 syscall + Policy + AuditSink + Secret 插头 |
| gap 显式债 = 下一刀必做 | 缺则不得暗示已具备；排期另裁 |
| 「大半对上」= Skill/MCP 也齐 | 仅工具行大半；另两行诚实写弱/未 |

---

## 6. EnvHarness × Gate × Observation（机制对账）

> 来源：[google-research/envharness](https://github.com/google-research/envharness)（[arXiv:2608.19880](https://arxiv.org/abs/2608.19880)）。**不是** Tepeu 规范；吸收姿态 = 几何同构，不立项训练管线。

### 6.1 总判断

EnvHarness 与 Tepeu 门禁是**同一物种**：在标准接口上插可编程壳，不改内核实现。

| EnvHarness | Tepeu |
|------------|--------|
| 拦在 `reset` / `step` | 拦在 syscall（取消 → 卫兵 → Policy → 分发） |
| Setup / Rule / Link | 初始边界 / Policy·卫兵 / 编排叠层（弱类比） |
| 不动 success verifier | 不动完成门、审批证据、AuditSink、`derive` 诚实性 |
| 可叠层 | 卫兵可叠；deny > ask > allow |

**机制收束**：Gate = 边界上跑脚本。教 agent、调难度、打弱点、拦 `rm`、熔断序列——都是**脚本内容**，不是 Gate 形状本身。

### 6.2 契约（训练与生产共用）

```
门脚本可动：初始态、合法动作、观测（含 syscall 参数/返回 shaping）
门脚本不可动：verifier（成功判定 / 合规裁决 / 已落账真相）
```

生产改观测是正当能力，不是教学特权：工具返回脱敏、越权统一 deny 观测、审批挂起时合成「需确认」等。  
错读：「生产安全脚本不应改观测」——那是脚本写得好不好，不是机制红线。红线是 **别动 verifier / 别伪造已落账真相**。

### 6.3 Observation 应是组件

「看见什么」单独拥有（已开 jar）：

| 碎片 | 归属 |
|------|------|
| `session` surface | 压缩后的模型读面 |
| `observation` `Observation.view` | derive ∘ normalize ∘ shape |
| `orchestration` PromptAssembly | system / 动态段（仍独立） |
| 零星合成 TOOL_RESULT | DoomLoop / 拦截占位 |

**目标分工**

```
Policy / 卫兵  → 判动作（能不能做）
Observation    → 判/造视图（看见什么）：surface + derive/normalize + shape/redact
Verifier       → 判成败与证据（entries / 完成门 / 审批）——门脚本碰不得
Gate           → 编排跑脚本；改观测时调用 Observation，不旁路拼消息
```

Observation **不是**旁路可见通道：仍服从「模型可见 ⟺ 日志可还原」与 `derive(log) ∘ normalize == sent`。  
已开 `os/observation/`（入口 `Observation.view`）。PromptAssembly 仍独立。EnvRigger（按失败轨迹自动改 Rule）是训练产品，**不**进 develop 主线。

### 6.4 禁止读法

| 误读 | 正确读法 |
|------|----------|
| EnvHarness = 你们的 ToolGate 产品 | 机制同构；目的可配置（教或防） |
| 观测可改 = 可改 entries / 完成门 | 只改视图管道；verifier 神圣 |
| 立刻拉 ALFWorld 栈进内核 | 参照；observation 已开，不抄教学环境 |

---

## 7. 与现有吸收文档的关系

| 文档 | 关系 |
|------|------|
| [work-docs-absorption.md](../../work-docs-absorption.md) | 闸门与诚实八条；本系列不插队立项 |
| [agent-os-gap.md](../../agent-os-gap.md) | 运行中撤销等债的权威诚实度入口 |
| [opencode-reference.md](./opencode-reference.md) | 权限配置范式可对照第 2 篇，不作正典 |
| 底板 §3.3 / §8.5 | Observation 组件候选；DoomLoop 等熔断落点 |
| EnvHarness 仓库 / 论文 | §6 机制对账入口 |
| [tencent-harness-engineering.md](./tencent-harness-engineering.md) | AI Coding 文；**与本系列/`os/` 不同线**，勿当吸收参照 |
| [ai-eval-observability-pipeline.md](./ai-eval-observability-pipeline.md) | 评测可观测运维；可借脱敏/诚实边界；非内核 |

**不**因本系列或 EnvHarness 新增 ADR，除非另行裁决切片。
