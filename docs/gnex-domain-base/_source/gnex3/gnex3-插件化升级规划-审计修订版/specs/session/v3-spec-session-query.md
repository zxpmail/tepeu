# v3-spec-session-query

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-query
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：observability-service
> **实现状态**：规划中

---

## §1 业务目标

会话查询/搜索：为 UI、导出、引用提供只读会话检索——按会话/项目/Agent/时间范围/关键词分页查询，对齐 dsh-session-query 的查询职责与 gnex2.0 observability Query Interface（只读消费状态面物化 DB，E14）。修复 2.0 P2 遗留（会话历史全量查询无 LIMIT/缓存）：首版强制分页上限 + 租户硬过滤。

**非目标**：不写任何数据（纯只读）；不做全文语义检索（首版关键词匹配，ES 演进留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-query-sqlite`（会话查询/搜索） |
| 对标 GNEX2.0 模块 | `gnex-observability-service`（Query Interface：`SessionArchiveQueryPort`/只读查询适配）+ agent-service `ChatSessionController` 会话查询 |
| 技术选型 | 🅶 GNEX2.0 模块改造（observability 查询接口收敛为插件服务） |
| 改造要点 | ① `sessionQuery` 服务包装只读 Query 接口：list/get/search/paginate ② 读 PG 读模型经 `storage` hub pg 后端（只读连接）③ 关键词搜索首版 PG tsvector/GIN，禁全表扫 ④ 所有查询强制 scope 过滤 + LIMIT |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionQuery` | `com.gnex.plugin.session.SessionQueryService` | listSessions(filter, page) / getSession(id) / search(keyword, filter, page) / count(filter) |

### 3.2 注入服务（inject）

`storage`（必选，pg 只读后端，domain: session）、`scope`（必选）、`settings`、`events`（可选，listen session/event 更新内存索引）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/event` | listen（可选） | 增量索引（首版可不订阅，直接查 PG） |
| `session/disposed` | listen（可选） | 标记归档/剔除活跃列表 |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-session-query
    load: mandatory
settings:
  sessionQuery:
    defaultPageSize: 20
    maxPageSize: 100      # 硬上限（修复全量查询 P2 遗留）
    searchMode: pg_tsvector  # 首版 PG GIN；ES 演进见 §8
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 所有查询必须带租户/项目 scope 硬过滤（E15：scope 先过滤再排序/打分）
- ✅ 分页必须受 maxPageSize 约束，禁止无 LIMIT 全量查询
- ✅ 关键词搜索必须走索引（tsvector/GIN），禁止 LIKE '%…%' 全表扫
- ❌ 不得写任何数据（连读模型都不得由本插件写）
- ❌ 不得跨租户返回（串租户 = P0）
- ❌ 不得把 Redis 热状态当查询权威源（读权威 = PG 读模型/日志）
- ❌ 不得在查询路径做内容改写/脱敏外逻辑

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 列表分页 | `SessionQueryListTest`：page/size 生效，总行数正确 |
| V2 | 上限约束 | `SessionQueryPageLimitTest`：size>maxPageSize 被截断为 maxPageSize |
| V3 | 关键词搜索 | `SessionQuerySearchTest`：命中与相关性排序正确（tsvector） |
| V4 | 租户隔离 | `SessionQueryScopeTest`：跨租户 filter 结果为空 |
| V5 | 组合过滤 | `SessionQueryFilterTest`：agent/项目/时间范围组合过滤 |
| V6 | 只读 | `SessionQueryReadOnlyTest`：写调用被拒（无写 SPI） |

### 运行命令

```bash
mvn -pl services/gnex-observability-service test "-Dtest=SessionQuery*Test"
```

## §7 影响域

- 作用范围：**会话级 + 租户级**（只读查询面）。
- 被谁消费：前端 UI（会话列表/搜索）、session-export、引用/审计查询。
- 故障影响半径：查询接口不可用 = 会话历史不可见（不影响 Agent 运行）。
- 升级影响：索引/搜索模式变更 = 查询行为变更（无数据迁移）。

## §8 后续（未纳入首版）

- ES 8 全文检索（触发线：审计/会话 >5000 万行，E16 演进）
- 查询结果缓存（Redis 短 TTL，失效经 session/event）
- 自然语言会话检索（向量，pgvector → Milvus）

---

*GNEX3.0 会话查询 — 只读检索面 SDD。*
