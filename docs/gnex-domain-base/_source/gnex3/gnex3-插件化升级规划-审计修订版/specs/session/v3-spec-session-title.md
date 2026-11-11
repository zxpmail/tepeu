# v3-spec-session-title

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-title
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

会话标题：为会话生成/更新可读标题，对齐 dsh-session-title 与 gnex2.0 既有的会话标题摘要能力。会话创建后异步取首条用户消息，经 `llm` 摘要为 ≤N 字标题，写入 `sessionPersistence`；不阻塞首 turn 热路径，空会话/敏感内容不生成。标题是纯展示元数据，不参与记忆/完成判定。

**非目标**：不做标题翻译/多语言（后续）；不做记忆写入；不参与完成证据。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-title`（会话标题生成） |
| 对标 GNEX2.0 模块 | agent-service 会话标题摘要（已有能力，收敛为 `sessionTitle` 服务） |
| 技术选型 | 🅶 GNEX2.0 模块改造（既有摘要能力插件化） |
| 改造要点 | ① 标题生成逻辑收敛为 `sessionTitle` 服务：listen `session/created` → 首条消息 → llm 摘要 → 落库 ② 异步执行（不阻塞首 turn），失败静默降级默认标题 ③ 复用 `llm` 服务（模型路由）与 `sessionPersistence`（写标题）④ 长度上限 + 空会话/工具内容过滤 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionTitle` | `com.gnex.plugin.session.SessionTitleService` | generate(sessionId) / update(sessionId, title) / get(sessionId) |

### 3.2 注入服务（inject）

`events`（必选，listen session/created）、`sessions`（必选，读首条用户消息）、`llm`（必选，摘要）、`sessionPersistence`（必选，写标题）、`scope`（必选）、`settings`、`jobs`（可选，异步任务）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/created` | listen | 触发标题生成（异步，不阻塞创建返回） |
| `turn/end` | listen（可选） | 标题为空/首轮后补生成 |

## §4 配置与装配

```yaml
settings:
  sessionTitle:
    enabled: true
    maxLength: 50          # 标题字数上限
    model: "default"       # 经 llm 路由（FAST 档）
    async: true            # 异步生成
    generateOnCreate: true
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 标题生成必须异步，不得阻塞首 turn / 会话创建返回
- ✅ 标题必须 ≤ maxLength；空会话/无用户消息不生成（保留默认标题）
- ✅ 标题必须经 `sessionPersistence` 落库（幂等 upsert，sessionId 唯一）
- ✅ 生成失败必须静默降级为默认标题，不得影响会话流程
- ❌ 不得把工具输出/错误堆栈/执行流水写入标题（对齐记忆写入闸精神）
- ❌ 不得把标题生成当记忆写入或完成证据（标题=纯展示元数据）
- ❌ 不跨租户读取消息生成标题（scope 硬隔离）
- ❌ 不得在标题中注入任何未脱敏密钥/路径

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 创建触发 | `SessionTitleGenerateTest`：session/created 后标题生成并落库 |
| V2 | 长度上限 | `SessionTitleLengthTest`：摘要 > maxLength 被截断 |
| V3 | 空会话 | `SessionTitleEmptyTest`：无用户消息不生成，保留默认标题 |
| V4 | 非阻塞 | `SessionTitleAsyncTest`：创建 API 返回时间不含 LLM 调用 |
| V5 | 失败降级 | `SessionTitleFallbackTest`：llm 异常 → 默认标题，会话不受影响 |
| V6 | 落库幂等 | `SessionTitlePersistTest`：重复生成 upsert 唯一行 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=SessionTitle*Test"
```

## §7 影响域

- 作用范围：**会话级**（展示元数据）。
- 被谁消费：前端会话列表/标题展示、session-query（列表字段）。
- 故障影响半径：标题服务故障 = 显示默认标题，不影响 Agent 运行。
- 升级影响：摘要 prompt/模型变更 = 新标题行为，历史标题保留。

## §8 后续（未纳入首版）

- 多语言标题（llm 翻译）
- 标题随会话演变更新（关键 turn 后刷新）
- 用户手动改标题与自动生成的冲突策略

---

*GNEX3.0 会话标题 — 展示元数据生成 SDD。*
