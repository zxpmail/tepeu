# v3-spec-context-pruner

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.10
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-pruner
> **分层**：L3 能力层（3.10 上下文与压缩）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service（gnex-agent-service）
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 依据 D-P1-28

---

## §1 业务目标
工具结果修剪插件：在工具执行管线 post-execute 阶段修剪超大工具输出，防止工具结果挤爆上下文（对齐 dsh-compaction-tool-result-pruner）。修剪是压缩的补充：compaction 管全局上下文体积，pruner 管单次工具结果体积。修剪策略可配置（体积阈值/类型白名单/截断规则），修剪必须留痕——被修剪的原文可恢复（经 spill 服务 API 移交，或保留于 session log），模型可见输入仍可从日志重建；禁止把 Agent 交付证据（Write/HTTP 证物，P0-a）修剪丢失。gnex2.0 无对应模块（新增）。

**非目标**：不做全局压缩（归 compaction）；不做溢出存储本体（归 spill，pruner 只负责修剪与移交）；不做摘要（归 compaction）。

## §2 映射关系
| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-compaction-tool-result-pruner`（工具结果修剪） |
| 对标 GNEX2.0 模块 | 无（新增）；复用 `gnex-agent-sdk`（InspectorChain / tools/post-execute 管线）+ `gnex-agent-persistence`（ExecutionLog） |
| 技术选型 | 🅳 Java 移植 dsh 实现 |
| 改造要点 | ① 监听 tools/post-execute（waterfall，**监听序固定 pruner → spill**，D-P1-28）② 修剪规则：体积阈值/类型白名单/截断（保留头尾+摘要指针）③ 修剪留痕：原结果**先经 spill 服务 API 移交**（原文先于截断落 spill，防"存残文"）或日志回退，禁止静默丢 ④ 证据保护：Write/HTTP 证物不修剪（P0-a 联动）⑤ 修剪痕迹 emit tools/result 供观测 |

## §3 插件接口（契约）
### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| （无公共服务） | — | 纯工具管线监听插件（经 tools/post-execute 生效） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`settings`、`logger`
- 可选：`spill`（修剪原文移交溢出存储）、`compaction` / `tokenMeter`（体积计量联动）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/post-execute` | listen | waterfall：超阈值结果修剪。**监听序（D-P1-28）：pruner（先，修剪并经 spill 服务 API 移交原文——原文先于截断移交，保证 spill 存全文）→ spill（后，溢出存储/引用补记）**；顺序由管线装载序固化（pruner 先于 spill 装载），非约定俗成 |
| `tools/result` | emit | 修剪后结果供观测（{tool, pruned, spillRef?}） |

> **spill 缺席回退（D-P1-28）**：spill（optional）缺席时，pruner 走**日志回退**——原文（或截断剩余全文）写入 session log 留痕（不静默截断无痕），`tools/result` 载荷不带 spillRef 而带 logRef；见 §6 V2b。

## §4 配置与装配
```yaml
# service-assembly.yml（agent-service）
plugins:
  - id: gnex3-cap-pruner
    load: optional
    order: before:gnex3-cap-spill   # D-P1-28：post-execute 监听序 pruner → spill
context:
  pruner:
    max-result-bytes: 65536      # 超阈值修剪
    keep-head-bytes: 4096        # 保留头部
    keep-tail-bytes: 1024        # 保留尾部
    protect-deliverables: true   # 交付证据不修剪（P0-a）
```

## §5 边界（禁止 ❌ / 必须 ✅）
- ✅ 必须经 tools/post-execute 修剪，策略可配置（体积/类型/截断）
- ✅ **监听序固定 pruner → spill**（D-P1-28）：pruner 截断前先经 spill 服务 API 移交原文，禁止"先截断后 spill 存残文"
- ✅ 修剪必须留痕：原文可恢复（spill 引用，或 spill 缺席时 session log 回退），禁止静默丢信息
- ✅ 修剪后结果必须仍可从 session log 重建进模输入（模型可见⟺已落盘）
- ✅ 交付证据保护：Write/HTTP 2xx 证物（P0-a）不得被修剪丢失
- ❌ 禁止修剪 Agent 交付证据/失败回退正文当证据的路径（P0-a 联动）
- ❌ 禁止把失败堆栈/工具流水当记忆写入（记忆写入闸：显式意图+白名单）
- ❌ 禁止在修剪前不计量体积就丢弃（必须有阈值判定依据）
- ❌ 禁止 spill 缺席时静默无痕截断（必须日志回退留痕）

## §6 验收
| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 超阈值修剪 + 顺序 | `ToolResultPrunerTest`：>max-result-bytes 被截断为头尾+引用；**post-execute 监听序 pruner 先于 spill**（D-P1-28） |
| V2a | 原文可恢复（spill 在场） | `PrunerRestoreTest`：pruner 移交的是**截断前原文**，spill 引用可取回全文 |
| V2b | 原文可恢复（spill 缺席，D-P1-28） | `PrunerSpillAbsentTest`：不装载 spill 时，修剪原文回退写入 session log（logRef 可重建），不静默无痕截断 |
| V3 | 进模可重建 | `PrunerLogTest`：修剪结果从 session log 重建一致 |
| V4 | 证据保护 | `PrunerDeliverableTest`：Write/HTTP 证物不修剪 |
| V5 | 计量依据 | `PrunerMeterTest`：修剪前后体积入 tokenMeter |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=ToolResultPrunerTest,PrunerRestoreTest,PrunerSpillAbsentTest,PrunerLogTest,PrunerDeliverableTest,PrunerMeterTest"
```

## §7 影响域
- 作用范围：**会话级 / 每工具调用**（05 §3.5：单次调用被拦截）。
- 故障影响半径：修剪器故障 = 单工具结果不修剪（上下文超窗风险），fail-closed 倾向不修剪。
- 升级影响：策略变更 = 无感；规则增删 = 无感（其余工具不动）。

## §8 后续（未纳入首版）
- 类型化修剪（结构化结果按 schema 裁剪，对齐 F 产物优先结构化引用）
- 与 spill TTL 联动的清理回收（修剪原文过期清理）

---

*GNEX3.0 工具结果修剪 — dsh-compaction-tool-result-pruner Java 移植（监听序 pruner → spill + 日志回退，D-P1-28）SDD。*
