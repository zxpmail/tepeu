# v3-spec-ent-audit

> **来源**：../../04-gnex3.0-plugin-catalog.md §L4 gnex3-ent-audit
> **负责人**：<owner>
> **插件 ID**：gnex3-ent-audit
> **分层**：企业层（L4）
> **受保护级别**：protected（🔒 平台持有，禁租户/Agent 装卸）
> **宿主服务**：platform-service
> **实现状态**：规划中

---

## §1 业务目标

审计日志是 GNEX 企业合规底线：统一采集身份/工具/沙箱/管理/交互五类 action 的审计事件，经消息总线异步发布，由状态面幂等物化到 audit_log 检索库，支撑审计查询与 CSV 导出。目标：① 审计写入不阻塞业务热路径（先入总线，引擎不等物化）；② 审计流水全链路可关联（tenant_id/project_id/session_id/run_id/msg_id + trace_id）；③ 90 天合规留存，DLQ 即 P1 告警并 requeue。插件化价值：`audit` 服务成为全部写路径的统一出口，业务插件只调 audit.record，不感知总线与存储细节。

**非目标**：不做审计内容业务分析/告警规则（归 observability）；不审计会话正文与提示词内容（只记元数据）；不做 ES 检索库（阶段一 PG 足够，审计 >5000 万行再演进）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | 无（dsh 无此原语；dsh 会话日志非合规审计） |
| 对标 GNEX2.0 模块 | platform-service `com.gnex.platform.audit.AuditLogService` + libs/gnex-auth-persistence `audit_log` 表 + `StandardTopics.AUDIT_LOG`（topic `audit_log`）+ state-plane `AuditLogProjector`/`AuditDlqAlertConfig` |
| 技术选型 | 🅶 GNEX2.0 模块改造为插件（AuditPublisher/ToolAuditPort → audit 服务） |
| 改造要点 | ① 插件化：`audit` 服务提供 record/publish/query/export，AuditLogService 与 AuthEventPublisher 收编为插件实现；② 统一走 MQ：业务侧 emit audit/record → 插件异步发布 `audit.{tenant}`（orderKey），state-plane audit-cg 幂等物化，不再直写；③ 目标态字段扩展 tenant_id/project_id/session_id/run_id/msg_id + trace_id；④ DLQ 钩子 P1 告警 + requeue，90 天 TTL 清理 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `audit` | `com.gnex.ent.audit.AuditService` | 审计记录/查询/导出：record(action, resource, outcome, ctx) / query(filters) / exportCsv() |

### 3.2 注入服务（inject）

- `bus`（必选）：跨服务发布审计消息（produceAsync → `audit.{tenant}`）
- `statePlane`（可选）：读已物化审计（查询/导出走只读面）
- `storage`（可选）：审计附件/导出文件落 MinIO
- `scope` / `settings` / `events`（必选）：租户 realm、配置段、事件总线

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `audit/record` | emit | {action, resourceType, resourceId, outcome, tenantId, projectId, sessionId, runId, traceId, actor} |
| `bus/published` | listen | {topic: audit.*, msgId} 发布结果（可选观测） |
| `bus/consumed` | listen | {topic: audit_log, msgId, group: audit-cg} 物化结果 / DLQ 告警 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-ent-audit
    load: mandatory
    protection: platform
    config:
      gnex.audit.retention-days: 90        # 合规留存，到期清理
      gnex.audit.dlq-alert: true           # DLQ 触发 P1 告警 + requeue
      gnex.audit.async: true               # 先入总线，不阻塞业务路径
      gnex.audit.export-limit: 1000        # CSV 导出默认上限（上限 10000）
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 审计必须异步发布（经总线），业务路径不阻塞；Agent 引擎不等物化
- ✅ 审计记录必须含五类关联键（tenant/project/session/run/msg_id）+ trace_id，action 枚举分组（身份/工具/沙箱/管理/交互）
- ✅ 物化必须幂等（msg_id 唯一约束，DuplicateKeyException 跳重）；DLQ 必须 P1 告警并 requeue（90 天合规）
- ✅ 查询/导出必须角色受限（SYSTEM_ADMIN/ADMIN/VIEWER），CSV 导出限流
- ❌ 审计不得直写业务库绕过总线（统一走 MQ，state-plane 物化）
- ❌ 租户/Agent 不得装卸审计插件（🔒）；审计记录不得被业务代码删除/篡改（仅 TTL 清理）
- ❌ 审计载荷不得包含密钥/明文令牌/会话正文（脱敏，只记元数据）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 业务插件 record → 总线发布 | `AuditPublishTest`：emit audit/record 后 `audit.{tenant}` 消息含五类关联键 |
| V2 | 状态面幂等物化 | `AuditMaterializationTest`：同 msgId 重投只落一行（唯一约束跳重） |
| V3 | 审计查询过滤 | `AuditQueryTest`：按 action/resourceType/from/to 过滤 + 分页 |
| V4 | CSV 导出 | `AuditExportTest`：limit 默认 1000 上限 10000，Content-Disposition attachment |
| V5 | DLQ 告警 | `AuditDlqTest`：audit_log 进 DLQ 触发 P1 日志告警钩子 |
| V6 | 无总线降级 | `AuditDegradeTest`：bus 缺失时降级本地日志，不抛异常 |

### 运行命令

```bash
mvn -pl services/gnex-platform-service test "-Dtest=Audit*Test"
```

## §7 影响域

- 作用范围：**全平台**（租户级）——所有写路径（登录/工具/沙箱/管理/交互）的审计出口。
- 被谁消费：state-plane（物化）、observability（审计查询/导出）、ops（合规审计面）。
- 故障影响半径：审计不可用不影响 Agent 运行（事件积压总线）；DLQ 积压 = 合规风险（P1）。
- 升级影响：🔒 平台级升级 + 灰度；schema 变更走迁移（audit_log 扩展列）。

## §8 后续（未纳入首版）

- ES 检索库（审计 >5000 万行演进线）+ tsvector/GIN 全文
- 审计内容业务告警规则（异常登录/权限变更，归 observability 告警）
- 审计导出异步任务（大导出走 jobs 服务，避免同步限流）

---

*GNEX3.0 企业审计日志插件 SDD。*
