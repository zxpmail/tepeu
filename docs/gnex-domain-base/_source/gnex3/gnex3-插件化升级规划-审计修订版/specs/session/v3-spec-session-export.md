# v3-spec-session-export

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.2（会话与持久化）
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-session-export
> **分层**：L3 能力层（3.2 会话与持久化）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：observability-service
> **实现状态**：规划中

---

## §1 业务目标

会话导出：把完整会话日志导出为可交换/可审计格式（JSONL / Markdown / HTML / 纯文本），对齐 dsh-session-log-export。gnex2.0 无直接对应，本插件在 observability 既有只读 Query 接口与 `gnex-persistence-common` 文件/产物设施（`FileStorePort`）上改造实现：经 `sessionQuery` 读取、按模板渲染、异步生成下载链接；导出前强制 scope 权限校验，默认脱敏密钥类内容。

**非目标**：不导出附件正文（只给引用/链接）；不做跨会话打包（后续）；不写会话数据。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-session-log-export`（会话日志导出） |
| 对标 GNEX2.0 模块 | 无直接对应；借力 observability Query Interface（E14 只读）与 `gnex-persistence-common`（`FileStorePort`/`LocalFileStore`/`MinioIamConfig` 输出） |
| 技术选型 | 🅶 GNEX2.0 模块改造（在 observability 查询/输出设施上新增导出能力） |
| 改造要点 | ① `sessionExport` 服务：export(sessionId, format, options) ② 读路径只走 `sessionQuery`（PG 读模型，禁直读 Redis 热状态）③ 渲染模板（JSONL 原样/Markdown/HTML）④ 异步任务（`jobs`）生成文件 → `FileStorePort` 产物 → 预签名下载链接 ⑤ 导出前 scope 校验 + 敏感字段脱敏 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `sessionExport` | `com.gnex.plugin.session.SessionExportService` | export(sessionId, format, options) → ExportTask / getExport(taskId) / downloadUrl(taskId) |

### 3.2 注入服务（inject）

`sessionQuery`（必选，读源）、`scope`（必选，权限校验）、`jobs`（必选，异步任务）、`storage`（可选，FileStore 产物）、`settings`、`attachments`（可选，产物引用解析）。

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `session/disposed` | listen（可选） | 归档时预生成导出（审计留档钩子） |

## §4 配置与装配

```yaml
settings:
  sessionExport:
    formats: [jsonl, markdown, html, text]
    maxEntriesPerExport: 10000
    async: true
    redactSecrets: true    # 默认脱敏密钥/token/路径
    ttlHours: 24           # 导出产物保留
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 导出前必须 scope 权限校验（租户/项目硬过滤，串租户 = P0）
- ✅ 读源必须经 `sessionQuery`（PG 读模型），禁止直读 Redis 热状态当正文
- ✅ 大会话（> maxEntries）必须异步导出，禁止阻塞查询线程
- ✅ 默认脱敏密钥/token/凭证类内容（对齐凭证管理纪律）
- ❌ 不导出附件正文（只给引用/预签名链接，正文归对象存储）
- ❌ 导出不得触发任何会话数据写（只读 + 产物文件生成）
- ❌ 不得导出跨租户/无权限会话（403）
- ❌ 导出文件不得明文落盘密钥（产物带租户桶 + IAM，E15）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | JSONL 导出 | `SessionExportJsonlTest`：条目逐行 JSON，顺序与日志一致 |
| V2 | 格式渲染 | `SessionExportFormatTest`：markdown/html 模板渲染正确 |
| V3 | 权限校验 | `SessionExportScopeTest`：跨租户导出 403 |
| V4 | 异步任务 | `SessionExportAsyncTest`：大会话任务异步完成，下载链接可访问 |
| V5 | 脱敏 | `SessionExportRedactTest`：含 token 的条目导出后已脱敏 |
| V6 | 只读 | `SessionExportReadOnlyTest`：导出过程无会话数据写入 |

### 运行命令

```bash
mvn -pl services/gnex-observability-service test "-Dtest=SessionExport*Test"
```

## §7 影响域

- 作用范围：**会话级 + 租户级**（只读导出面）。
- 被谁消费：前端导出按钮、审计留档、合规取证。
- 故障影响半径：导出不可用 = 无下载（不影响 Agent 运行与查询）。
- 升级影响：模板/格式变更 = 新导出行为，历史产物 TTL 清理。

## §8 后续（未纳入首版）

- 批量/跨会话导出（审计合规打包）
- PDF/Word 富格式导出
- 导出产物生命周期管理（对象存储 LRU，90d）

---

*GNEX3.0 会话导出 — 只读导出面 SDD。*
