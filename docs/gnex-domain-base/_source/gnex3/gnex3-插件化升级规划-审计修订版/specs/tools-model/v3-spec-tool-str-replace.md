# v3-spec-tool-str-replace

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-08 / D-P1-11 / P2-06 / P2-07**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-str-replace
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

给模型提供行级文本编辑工具（view / str_replace / insert / create / undo），替代整文件覆写，减少误改与 diff 噪音。写操作必须经工具管线（tools/pre-execute 风险分级 → approval 审批地板），路径经 sandbox 映射校验防逃逸；写成功后发布统一写证据 `writeEvidence { paths, hash, turnId, ts }`（工具成功后 emit `write-evidence`，gate 消费——契约见 05 §4，D-P1-08），供 `gate` 完成证据门消费（P0-a：故意失败 Write 不得 completed）。**非目标**：多文件原子提交/冲突检测与二进制编辑（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-str-replace-editor` |
| 代码实证来源 | agent-service `ClaudeWorkspaceWriteTool`（沙箱写工具，能力子集）`[设计态]` + FileSystemTools `[spring-ai 依赖]` 参考 |
| 技术选型 | 🅶 gnex2.0 模块改造 |
| 改造要点 | ① 保留 2.0 ClaudeWorkspaceWriteTool 沙箱写能力，扩展为 str_replace 语义（view/str_replace/insert/create/undo） ② 写操作必须走工具管线：tools/pre-execute 风险分级 → approval 审批（E 审批地板） ③ 写成功后 emit `write-evidence`（统一契约 writeEvidence，D-P1-08），供 gate 完成证据门消费（P0-a） ④ 路径必须经 sandbox 路径映射，禁止逃逸会话盘 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

无（纯工具插件，经 tools 注册表暴露工具回调）。

### 3.2 注入服务（inject）

- `tools`（必选）：注册 str_replace 工具回调；`fs`（必选，P2-07 统一命名）：文件系统 seam 与路径映射（与 tool-fs 注入同一 `fs` 服务，不叫 `sandbox`）
- `approval`（必选）：高风险写操作审批（E 审批地板）；`scope`（必选）：会话盘边界
- `events`（必选）：tool/call · tool-evidence 管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 工具特有预检（D-P1-11）：目标文件可编辑性/大小上限；路径越界校验归管线 Path guard |
| `tools/execute` | listen（waterfall） | 本体：view/str_replace/insert/create/undo + 落盘 |
| `tools/post-execute` | listen | undo 快照清理 |
| `tool/call` | emit | {toolName, path, operation} |
| `tool/result` | emit | {toolName, path, diff} |
| `write-evidence` | emit | 统一写证据（D-P1-08）：`writeEvidence { paths: string[], hash: string, turnId: string, ts: number }` → gate 消费（契约见 05 §4） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-tool-str-replace
    load: mandatory
gnex.tool.str-replace:
  max-file-bytes: 524288   # 单文件编辑上限
  max-diff-lines: 100      # 返回 diff 行数上限
  require-approval: true   # 高风险写默认入队审批
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 写操作必须经 sandbox 路径映射与会话盘边界校验；高风险写（会话盘外/大文件覆盖）必须入队 approval，同 turn 回放（E 审批地板）
- ✅ 写成功后必须 emit `write-evidence`（统一契约 writeEvidence { paths, hash, turnId, ts }，D-P1-08），供 gate 完成证据门消费；每次写操作必须返回 diff/行级结果供模型确认
- ✅ 职责界定（D-P1-11）：工具侧 pre-execute 仅做特有预检（文件大小/未读保护），路径穿越/越界为管线 Path guard 权威
- ❌ 禁止路径穿越（../、绝对路径越界、符号链接逃逸）；禁止静默覆盖未读文件（先 view 再 str_replace）
- ❌ 禁止绕过工具管线直改文件（审计/审批旁路）；写失败不得发布写证据（P0-a：故意失败 Write 不得 completed）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 基础操作 | `StrReplaceEditTest`：view/str_replace/insert/create 正确落盘 |
| V2 | 路径防护 | `StrReplacePathGuardTest`：越界/穿越路径拒绝（管线 Path guard） |
| V3 | 审批 | `StrReplaceApprovalTest`：高风险写默认入队，approval 批准后执行 |
| V4 | 证据 | `StrReplaceEvidenceTest`：写成功 emit write-evidence（writeEvidence 含 paths/hash/turnId/ts）；故意失败不 emit（P0-a） |
| V5 | 未读保护 | `StrReplaceUnreadTest`：未 view 直接 str_replace 拒绝 |
| V6 | undo | `StrReplaceUndoTest`：undo 恢复原文 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service -am test "-Dtest=StrReplace*Test"
```

## §7 影响域

- 作用范围：**会话级**——每工具调用。
- 被谁消费：agentLoop（工具结果）、approval（审批队列）、gate（写证据 → 完成门，经 write-evidence）；故障影响半径：单次写失败可回滚，审批拦截 = 单次调用待办。
- 升级影响：工具装卸无感（05 §3 L3.6）；writeEvidence 载荷变更 = gate 消费方同步（05 §4 契约为 SSOT）。

## §8 后续（未纳入首版）

- 多文件原子提交（事务性写）；并发写冲突检测（同一文件多会话）
- 文件级权限预设联动（permission）

---

*GNEX3.0 文本替换编辑器工具插件 — str_replace 语义 + 审批/统一证据门 SDD（审计修订版）。*
