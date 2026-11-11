# v3-spec-tool-fs-search

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-fs-search
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

工作区搜索工具（glob/grep）：Agent 在允许根内做文件名匹配与内容检索。grep 结果按行截断（防止整文件灌入上下文，配合 pruner/compaction），glob 结果限数量；二进制/超大文件跳过并提示。搜索根限定会话工作区（经 `fs` 服务路径映射），防 `..` 穿越与符号链接逃逸。

**非目标**：不做企业级全文检索（ES 归存储检索层）；不做语义检索（向量归记忆/知识库）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-fs-search`（glob/grep） |
| 对标 GNEX2.0 模块 | agent-service `GrepTool` + `GnexGlobTool` |
| 技术选型 | 🅶 GNEX2.0 模块改造（Grep/GlobTool → 管线工具插件） |
| 改造要点 | ① 注册 `tool_fs_glob`/`tool_fs_grep` 到 `tools` ② 搜索根 = 允许根（fs 服务路径映射，防 `..`/符号链接逃逸）③ grep 结果按行截断 + 超限提示（不吞全文）④ 二进制/超大文件跳过并提示 ⑤ 结果带相对路径供 fs 工具引用 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `fsSearchTools` | `FsSearchTool` | glob/grep 实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `fs`（必选）— 搜索根/路径映射
- `scope` / `events`（必选）— 会话隔离与管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 根校验 + 大小/类型预检 |
| `tools/execute` | listen（waterfall） | 本体：glob/grep + 截断 |

## §4 配置与装配

```yaml
gnex.tools.fs-search:
  max-glob-results: 500
  max-grep-lines: 200
  max-file-bytes: 1048576        # 超限文件跳过
  ignore: [".git", "node_modules", "target", "*.lock"]
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 搜索根必须限定在允许根内（经 fs 服务映射，防 `..`/符号链接逃逸）
- ✅ 结果必须截断并提示截断（大结果不得整灌上下文）
- ✅ 二进制/超大文件必须跳过并说明原因
- ❌ 不得搜索允许根之外路径（工作区外检索禁止）
- ❌ 不得把超限文件全文返回（防上下文爆炸，配合 pruner）
- ❌ 搜索不得执行用户内容（grep 纯文本匹配，无 shell 语义）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | glob 匹配 | `FsGlobTest`：模式匹配返回相对路径列表，限数量 |
| V2 | grep 行匹配 | `FsGrepTest`：返回匹配行 + 行号，超限截断并提示 |
| V3 | 根逃逸拒绝 | `FsSearchEscapeTest`：`../` 搜索 → deny |
| V4 | 大文件跳过 | `FsSearchSkipTest`：超限/二进制文件跳过且提示 |
| V5 | 忽略清单 | `FsSearchIgnoreTest`：ignore 模式不进入结果 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=FsSearch*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）。
- 被谁消费：Agent 循环；pruner/compaction（上下文裁剪联动）。
- 故障影响半径：单工具不可用；搜索缺陷 = 上下文膨胀（截断为硬约束）。
- 升级影响：工具装卸 = 无感。

## §8 后续（未纳入首版）

- 正则跨行/多文件聚合（ripgrep 高级语义）
- 搜索结果缓存（同根同模式命中复用）
- 与 LSP 符号索引联动（platform-service LspService 复用）

---

*GNEX3.0 工作区搜索工具 — gnex3-cap-tool-fs-search SDD。*
