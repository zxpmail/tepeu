# v3-spec-context-compaction

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.10
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-compaction
> **分层**：L3 能力层（3.10 上下文与压缩）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service（gnex-agent-service）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-27（含审计 P1-3/P2-4 修正）

---

## §1 业务目标
上下文压缩插件：以 token 计量驱动 + LLM 摘要，控制进模上下文体积，防止上下文超窗（05 影响域 §3.10：压缩故障 = 上下文超窗）。对齐 dsh-compaction-basic + dsh-token-meter 语义：tokenMeter 计量每次 LLM/工具调用体积，超阈值触发压缩；摘要经 llm 服务生成并作为会话事件落盘（「模型可见 ⟺ 已落盘」）。继承 gnex2.0 已有上下文感知压缩（改造），补齐 token 驱动阈值与可审计摘要，修复「上下文靠事故补丁」缺口（gnex-spec-constraints §五-5）中的压缩环节——成为不可绕过的进模前处理。

**非目标**：不做工具结果修剪（归 pruner）；不做溢出存储（归 spill）；不做企业记忆（压缩 ≠ 温冷 SoR，P0-b）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-compaction-basic`（压缩策略）+ `dsh-token-meter`（token 计量驱动） |
| 对标 GNEX2.0 模块 | 上下文感知压缩（已有，改造）；`gnex-llm-adapt`（ModelRouter 摘要调用）+ `gnex-agent-sdk`（TokenJuice / TokenSaver） |
| 技术选型 | 🅶 已有上下文感知压缩改造为 token 驱动 |
| 改造要点 | ① tokenMeter 服务：LLM/工具结果体积计量（对齐 dsh-token-meter）② 压缩触发 = token 阈值（可配置，超窗保护）③ 摘要经 llm 生成，作为 session 事件落盘（可审计可重建）④ 原始消息保留在 session log（压缩只改进模形态）⑤ 压缩不可绕过（agent/request waterfall 前置）⑥ 降级阶梯（D-P1-27，见 §5） |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `compaction` | `com.gnex.plugin.context.CompactionService` | 压缩策略：阈值判定/摘要生成/进模替换/降级阶梯 |
| `tokenMeter` | `com.gnex.plugin.context.TokenMeter` | token 计量（05：由本插件提供，供压缩/UI） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`llm`（摘要生成）、`sessions`（事件落盘）、`settings`、`logger`
- 可选：`tokenMeter`（若拆包则注入；首版同包自带）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/request` | listen | waterfall：超窗触发压缩后放行 |
| `tools/post-execute` | listen | 工具结果体积计量（入 tokenMeter；监听序见 pruner/spill spec，D-P1-28） |
| `compaction/triggered` | emit | {scope, beforeTokens, afterTokens, summaryId, strategy}（strategy = summary/truncate） |

## §4 配置与装配
```yaml
# service-assembly.yml（agent-service）
plugins:
  - id: gnex3-cap-compaction
    load: mandatory        # 进模前处理，不可绕过（缺失 = 装配校验启动失败，见 V5）
context:
  compaction:
    token-threshold: 48000        # 超阈值触发压缩
    summary-model: deepseek-chat  # 摘要模型（经 llm 路由）
    keep-original: true           # session log 保留原文
    fallback: truncate            # 摘要失败降级策略（D-P1-27）
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须 token 驱动：tokenMeter 计量，超阈值触发（阈值可配置）
- ✅ 摘要必须作为会话事件落盘（可审计、可从日志重建进模输入）
- ✅ 必须保留原始消息（session log append-only 不受压缩影响，对齐「模型可见⟺已落盘」）
- ✅ 压缩必须为 agent/request 前置不可绕过（进模前处理不变量；**强制点 = 装配校验**：装配清单缺失本插件时 agent-service 启动失败）
- ✅ **降级阶梯（D-P1-27）**：摘要 LLM 失败 → **截断式压缩**（保留头尾 + 留痕事件 `compaction/triggered{strategy:truncate}`）→ **仍失败才拒绝 agent/request**（fail-closed）；禁止摘要单点故障直接放大为全会话不可用，也禁止静默跳过压缩
- ❌ 禁止把压缩/checkpoint 当企业记忆 SoR（P0-b：checkpoint Redis 禁混称记忆平面）
- ❌ 禁止口头回合/子会话盲压缩丢弃关键事实（分角色进模纪律，缺口#5）
- ❌ 禁止摘要覆盖原文（原文仍在事件流，摘要只是进模形态）
- ❌ 禁止截断式降级不留痕（truncate 必须发留痕事件并可从日志重建）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | token 阈值触发 | `ContextCompactionTest`：超阈值压缩、未超不压缩 |
| V2 | 计量准确性 | `TokenMeterTest`：LLM/工具结果计量与模型计价一致 |
| V3 | 摘要落盘可重建 | `CompactionAuditTest`：摘要事件进 session log，可重建进模输入 |
| V4 | 原文保留 | `CompactionOriginalTest`：压缩后 session log 原文完整 |
| V5 | 不可绕过（装配校验） | `CompactionMandatoryTest`：装配清单缺失本插件时 agent-service 启动失败（装配校验报错），而非运行期拒绝 |
| V6 | 降级阶梯（D-P1-27） | `CompactionFallbackTest`：①摘要 LLM 失败 → 截断式压缩（头尾保留 + `strategy:truncate` 留痕事件 + session log 可重建）②截断亦失败 → agent/request 拒绝（fail-closed）；③摘要故障不得导致静默跳过压缩 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ContextCompactionTest,TokenMeterTest,CompactionAuditTest,CompactionOriginalTest,CompactionMandatoryTest,CompactionFallbackTest"
```

## §7 影响域
- 作用范围：**会话级**（05 §3.10：上下文组装）；被 agentLoop/命令/UI（token 用量）消费。
- 故障影响半径：摘要故障 = 降级截断（留痕）；全链路故障 = 该会话 agent/request 拒绝（fail-closed，不超窗进模）；不影响其它会话与主进程。
- 升级影响：策略变更 = 无感；摘要格式变更 = 历史会话重建兼容（版本化）。

## §8 后续（未纳入首版）
- 分角色进模策略对照表（缺口#5 F：产物优先结构化引用）
- 压缩成本预算联动（对齐 E22 计费：摘要 token 计入 token_usage）

---

*GNEX3.0 上下文压缩 — token 驱动 + LLM 摘要 + 降级阶梯（dsh-compaction-basic + dsh-token-meter 对齐；D-P1-27）SDD。*
