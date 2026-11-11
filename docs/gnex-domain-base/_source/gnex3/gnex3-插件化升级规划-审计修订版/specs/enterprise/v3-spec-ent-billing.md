# v3-spec-ent-billing

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-billing
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-billing
> **分层**：企业层（L4）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：ops-service（计费/配额管理面）
> **实现状态**：规划中

---

## §1 业务目标

计费/配额/熔断的企业控制面：继承 gnex2.0 ops/billing——模型网关旁路写 `token_usage`/`cost_events`（谁产生谁记账，E29），运营只读聚合；预算 `billing_budget`（monthlyBudgetUsd + alertThresholdPct 80% + hardLimitPct 120% + autoDowngrade），`BudgetCheckScheduler` 每小时巡检三级告警（WARNING/EXCEEDED/CIRCUIT_BREAK）写 Redis `tenant:cost-circuit:*` 熔断；租户配额 `tenant_quota`（maxAgents/maxProjects/maxMembers/maxSandboxes/monthlyTokenBudget/storageQuotaMb/vectorQuotaMb）。插件化为 `billing` 服务：配额超限拒绝创建、预算熔断联动网关/运行时限流，全部事件化（E19/E22/E29）。

**非目标**：不做模型网关本身（归 ent-model-gateway 路由）；不做支付/发票（企业账单对接留后续）；不做沙箱三级配额（归 sandbox-policy，本插件只读聚合）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh 无此原语（dsh 无企业计费/配额平面） |
| 对标 GNEX2.0 模块 | `ops-service` billing 包（`BillingService`/`BillingBudgetMapper`/`CostAlertMapper`/`BudgetCheckScheduler`/`BillingController`）+ `TenantService`（tenant_quota）+ `gnex-llm-adapt` `LlmCircuitBreaker`（租户级外层熔断）+ platform-persistence `TokenUsage`/`ModelPricing` |
| 技术选型 | 🅶 GNEX2.0 模块改造（ops billing 保留，插件化 + 事件化） |
| 改造要点 | ① 计费聚合收敛为 `billing` 服务（token_usage 只读聚合）② 三级预算巡检事件化（`billing/alert`）③ 熔断状态外化 Redis `tenant:cost-circuit:*` 月末 TTL，网关/运行时限流生效 ④ 配额检查接入资源创建入口（agent/project/member/sandbox）⑤ 超支 autoDowngrade 信号化 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `billing` | `com.gnex.plugin.enterprise.billing.BillingService` | 用量聚合/预算读写/配额检查/熔断查询（overview/projects/models/budget/alerts） |

### 3.2 注入服务（inject）

- 必选：`ctx`、`events`、`scope`、`tenant`、`audit`
- 可选：`observability`（用量指标）、`auth`（租户状态联动）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `billing/alert` | emit | {tenantId, level: WARNING\|EXCEEDED\|CIRCUIT_BREAK, budgetUsd, spentUsd}：三级告警 |
| `cost/record` | listen | {tenantId, runId, model, tokens, costUsd}：模型网关旁路记账（cost_events 语义） |
| `audit/record` | listen（可选） | 预算变更/配额变更入审计 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段（ops-service）
plugins:
  - id: gnex3-ent-billing
    load: mandatory
settings:
  billing:
    budget: { monthlyBudgetUsd: 0, alertThresholdPct: 80, hardLimitPct: 120, autoDowngrade: true }
    scheduler: { cron: "0 0 * * * ?" }  # 每小时巡检
    circuit: { redisKeyPrefix: "tenant:cost-circuit:", ttl: "endOfMonth" }
    quota: { maxAgents: 0, maxProjects: 0, maxMembers: 0, maxSandboxes: 0 }  # 0=平台默认
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 用量必须来自 `token_usage`/`cost_events` 旁路聚合（谁产生谁记账；模型网关每次 LLM 调用写 cost_events，E29）
- ✅ 预算必须三级：80% 告警 / 120% 硬限熔断 / autoDowngrade 自动降级
- ✅ 熔断状态必须外化（Redis `tenant:cost-circuit:*`）并被网关/运行时限流消费
- ✅ 配额超限必须拒绝新资源创建（agent/project/member/sandbox，tenant_quota 检查）
- ❌ 禁止绕过计费直接调用模型（所有 LLM 调用必须过 cost_events 记账）
- ❌ 禁止租户绕过/关闭预算熔断（hardLimit 不可豁免）
- ❌ 禁止跨租户用量聚合/预算可见（scope 硬过滤）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 用量聚合正确 | `BillingAggregateTest`：cost_events → token_usage 按月/项目/模型聚合一致 |
| V2 | 三级告警触发 | `BillingAlertTest`：80% WARNING / 120% EXCEEDED / 超限 CIRCUIT_BREAK |
| V3 | 熔断外化生效 | `BillingCircuitTest`：CIRCUIT_BREAK 写 `tenant:cost-circuit:*`，网关限流拒绝 |
| V4 | autoDowngrade | `BillingDowngradeTest`：超预算自动降级信号发出（agent_downgrade） |
| V5 | 配额拒绝 | `BillingQuotaTest`：maxAgents 达限后创建 Agent 被拒（409） |
| V6 | 租户隔离 | `BillingIsolationTest`：跨租户聚合/预算不可见 |
| V7 | 审计 | `BillingAuditTest`：预算变更/告警入 audit/record |

### 运行命令

```bash
mvn -pl services/gnex-ops-service test "-Dtest=BillingAggregateTest,BillingAlertTest,BillingCircuitTest,BillingDowngradeTest,BillingQuotaTest,BillingIsolationTest,BillingAuditTest"
```

## §7 影响域

- 作用范围：**租户级/平台级**——计费、配额与熔断。
- 被谁消费：模型网关（限流）、资源创建入口（配额）、ops 计费页、ent-org（成员配额联动）。
- 故障影响半径：熔断失效 = 超支（成本风险）；误熔断 = 该租户模型调用被拒（限流，不丢数据）。
- 升级影响：预算/配额语义变更 = 租户成本行为变化（灰度 + 审计追踪）。

## §8 后续（未纳入首版）

- 企业账单/发票对接（billing 报表导出）
- 沙箱成本精细计量（sandbox_usage 聚合入 overview）
- 弹性配额（按负载动态调整 quota，联动 ent-ha）

---

*GNEX3.0 计费与配额 — 预算/熔断控制面 SDD。*
