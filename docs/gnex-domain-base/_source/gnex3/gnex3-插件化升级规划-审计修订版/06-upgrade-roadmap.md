# GNEX3.0 升级改造路线图（工程化 · 阶段 · 依赖 · 验收门）

> **状态**：审计修订版 v0.2（2026-08-18，全量接纳审计/04 难度评估）· 关联：审计/04 · 审计修订版/02-设计决策记录.md
> **排期原则**：继承 gnex 既有优先级（P0-a 完成门 → P0-b 记忆 → P1 E 审批 → P2 观测），插件化**不得插队**这些硬约束的落地；插件化为这些约束提供"受保护插件"载体。

---

## 1. 阶段总览

| 阶段 | 名称 | 目标 | 关键产出 | 验收门 |
|------|------|------|----------|--------|
| S0 | 项目基建 | 任务书 + 分析基线 | 00-PROJECT-TASKS + _work/ 分析 | 本文档 + 评审 |
| S1 | 内核落地 | cordis-jvm 最小内核（含线程模型/Spring 桥规范/双端一致性测试套，D-P1-04/12/13） | L0/L1 插件 + 新模块 libs/gnex-plugin-runtime 等（D-P1-02） | 内核单测 V1-V10 + 装配演示（2 服务） |
| S2 | 试点改造 | 2-3 个服务内改造 | 工具管线+Inspector 融合 · 会话事件化 · 模型路由注册表化 | 试点服务验收（V1-Vn） |
| S3 | 能力迁移 | 按插件目录批量改造（S3 首批先做 S4 依赖插件：storage-hub/stateplane/audit/tenant，D-P1-03） | L3 能力插件（52 个） | 每插件 SDD 验收 + 迁移对照测试 |
| S4 | 企业面插件化 | L4 企业层插件化（ent-memory 单列 6-10 人周 + 数据迁移，D-P1-03） | 受保护插件 + 清单随镜像不可变（D-P1-04c） | P0-a/P0-b/E 硬约束验收 + 混合插件集回归 |
| S5 | 前端模块化 | L5 客户端模块化 | 前端功能模块 | 前端验收 |
| S6 | v3.1 热装（按需评估，D-P1-14） | 冷装+滚动重启为企业基线；热装降为按需评估（仅无状态工具类可热装） | PF4J PoC（类加载泄漏/依赖冲突验收） | 评估门 |
| S7 | JS 桥（v3.2 可选） | dsh 插件桥 | gnex3-bridge-dsh-js（GraalJS） | PoC 验收 |

---

## 2. 阶段依赖关系

```
S0 基建
 └─▶ S1 内核（cordis-jvm + 装配清单）
      ├─▶ S2 试点（依赖内核稳定）
      │    └─▶ S3 能力迁移（依赖试点模式固化）
      │         ├─▶ S4 企业面（依赖 L1 scope/事件就绪）
      │         └─▶ S5 前端（可与 S3 并行）
      ├─▶ S6 热装（依赖 S1-S3 稳定，v3.1）
      └─▶ S7 JS 桥（依赖 S3，v3.2）
```

**关键路径**：S0 → S1 → S2 → S3 → S4。预计工作量（人周，粗估）：S1 内核 4-6 人周；S2 试点 4-6 人周；S3 能力迁移 16-28 人周（52 插件 × 0.5-1 人周，可并行）；S4 企业面 8-12 人周（涉及 🔒 硬约束，评审重）；S5 前端 4-8 人周。

---

## 3. 每个阶段的任务清单

### S1 内核落地
- [ ] cordis-jvm：Ctx / Service / Plugin / Fiber / Events(waterfall+emit) / Effect / Scope（`specs/plugin-runtime/v3-spec-plugin-runtime-kernel.md`）
- [ ] service-assembly.yml 格式与解析器（含 protected 级别、id 覆盖语义）
- [ ] Spring 双向桥（@PluginService / PluginBridge）
- [ ] 插件加载器（模块组合 + 依赖等待 + 卸载回卷）
- [ ] 验收：内核单测（生命周期/依赖/事件/回卷/scope）；2 个服务（agent-service + registry-service）装配清单可启动

### S2 试点改造（选 3 个最能证明价值的）
1. **工具管线融合**（gnex3-cap-tools）：ToolCallback + InspectorChain → tools/pre-execute waterfall；Inspector 作为安全插件（Loop/Permission/Sandbox/Path/Risk/Security）
2. **会话事件化**（gnex3-cap-session-store）：agent_run_event → session/event + session/flush 词汇；SessionLoop 发 turn/step/tool 事件
3. **模型路由注册表化**（gnex3-cap-model-llm）：LLMConfigManager → provider 注册表 + retry + default
- [ ] 每试点：SDD + contracts + 单测 + 验收断言

### S3 能力迁移（按 04 目录 52 个能力插件）
- [ ] 批量：每插件一个 Maven 模块（`plugins/gnex3-<域>-<功能>`）+ SDD（specs/）+ contracts（libs/gnex-contracts 扩展）
- [ ] 分组并行（模型/会话/存储/工具/委派/目标/沙箱/上下文 8 组）
- [ ] 存量迁移：gnex2.0 模块改造成插件时**行为不回归**（迁移对照测试）

### S4 企业面插件化（🔒 受保护，评审最重）
- [ ] tenant / gate（P0-a 统一出口）/ memory（P0-b 平面）/ approval（E 原语）/ auth / audit / observability / state-plane / bus / billing / org / api
- [ ] 硬约束验收：P0-a 过线最低条 4 条；P0-b 路径 A 或 B；E 原语 3 条；租户边界硬过滤 —— **插件化不得放松任何一条**
- [ ] 双副本/弹性回归：多副本部署 + 弹性伸缩 + 滚动升级测试

### S5 前端模块化
- [ ] 前端内核（路由/状态/SSE 解析）与功能模块边界
- [ ] Operations 16 Tab 模块化、设置/权限/审计模块化

### S6 热装（v3.1）
- [ ] PF4J 类加载器后端 + 插件 jar 打包 + 版本管理 + 热装验收门

### S7 JS 桥（v3.2）
- [ ] GraalJS 桥 PoC（2-3 个 dsh 工具插件）+ shim + 沙箱强制 + 供应链检查

---

## 4. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 内核自研质量 | S1 不稳则全盘 | 内核先行 + 单测 + 2 服务试点后再铺开 |
| 存量行为回归 | 迁移破坏现网 | 迁移对照测试 + 灰度（配置开关） |
| 硬约束被插件化放松 | P0 倒退 | 🔒 protected 机制 + S4 验收门独立于插件化评审 |
| 插件数量膨胀（81） | 维护成本 | 分组并行 + 模板化 SDD + 契约先行 |
| 团队 SDD 工作量 | 交付延迟 | SDD 模板 + AI 辅助生成 + 先核心后叶子 |

---

## 5. 与既有排期的关系（不插队声明）

- 插件化是**工程架构改造**，不是 P0 功能补丁；P0-a/P0-b/E 的落地**优先于或并行于** S4，且插件化必须承载其原语（gate/memory/approval 插件）。
- 既有"无纳入"裁决（对齐 dsh/Cordis 换核）不因本规划复活换核主张；本规划只做服务内组合层插件化。
- 既有 SDD 纪律（spec 5 要素 / contracts / @spec 锚点 / STRUCTURE-RULES Invariant）在 v3 全量继承。

---

*待补：01-gnex2.0-architecture.md（依赖服务分析）· architecture.html · README.md 索引 · SDD 批量。*
