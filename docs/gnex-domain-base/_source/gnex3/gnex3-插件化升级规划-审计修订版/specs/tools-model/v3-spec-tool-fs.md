# v3-spec-tool-fs

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-08 / D-P1-11 / D-P2-11**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-fs
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

文件系统工具（read/write/edit）：Agent 在工作区内读写/编辑文件，是"完成=证据"（P0-a）的核心写路径。所有访问经 `fs` 服务（gnex3-cap-sandbox 的 fs seam + SandboxPathMapper 路径映射），限定在会话工作区/允许根内；write 成功必须真实落盘（fsync + 存在性校验）并发布统一写证据 `writeEvidence { paths, hash, turnId, ts }`（工具成功后 emit `write-evidence`，gate 消费——契约见 05 §4，D-P1-08）。覆盖/删除等高影响写操作走 Approval（缺席审批=deny，fail-closed）。

**非目标**：不做全文检索（gnex3-cap-tool-fs-search）；不做二进制大文件（附件走 attachments）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-fs`（read/write/edit） |
| 代码实证来源 | agent-service `FileSystemTools` `[spring-ai 依赖]` + `ClaudeWorkspaceWriteTool`（工作区写）`[设计态]` + `SandboxPathMapper` `[源码实证]` |
| 技术选型 | 🅶 GNEX2.0 模块改造（FileSystemTools → 管线工具插件） |
| 改造要点 | ① 注册 `tool_fs_read`/`tool_fs_write`/`tool_fs_edit` 到 `tools` ② 路径经 fs 服务映射（会话盘/沙箱路径，防 `..` 穿越与符号链接逃逸）③ write 落盘校验（fsync + 存在性确认）后 emit 统一写证据 `writeEvidence { paths, hash, turnId, ts }`（D-P1-08）④ 编辑用 apply-patch 语义（diff 可回放）⑤ 覆盖/删除标记高影响 → Approval |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `fsTools` | `FsTool` | read/write/edit 实现（注册进 `tools`） |

### 3.2 注入服务（inject）

- `tools`（必选）— 注册工具定义
- `fs` / `sandboxPolicy`（必选/可选）— 文件系统 seam（路径映射）与写盘档位
- `gate`（必选）— 完成证据门：消费 `write-evidence`（writeEvidence 载荷）
- `scope` / `events`（必选）— 会话隔离与管线收发

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `tools/pre-execute` | listen | 工具特有预检（D-P1-11）：落盘能力/只读挂载检查；路径校验归管线 Path guard（见 §5） |
| `tools/execute` | listen（waterfall） | 本体：读/写/编辑 + 落盘校验 |
| `tools/post-execute` | listen | 编辑回滚句柄释放 |
| `write-evidence` | emit | 统一写证据（D-P1-08）：`writeEvidence { paths: string[], hash: string, turnId: string, ts: number }` → gate 消费（契约见 05 §4） |

## §4 配置与装配

```yaml
gnex.tools.fs:
  roots: ["{workspace}", "{session-dir}"]
  max-read-bytes: 1048576
  overwrite-needs-approval: true   # 覆盖/删除同属高影响写，走 Approval
  fsync-on-write: true
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 读写必须经 `fs` 服务并在允许根内（路径映射 + 规范化，防穿越/符号链接逃逸）
- ✅ write 必须真实落盘（fsync + 存在性校验）后 emit 统一写证据 `write-evidence`（writeEvidence { paths, hash, turnId, ts }，D-P1-08；发布时机=工具成功后由工具发布，gate 消费）
- ✅ 覆盖/删除为高影响操作，走 Approval，缺席审批=deny
- ✅ **职责界定（D-P1-11）**：工具侧 `tools/pre-execute` listen 仅做工具特有预检（落盘能力/只读挂载/文件大小）；越根/穿越/符号链接等路径校验为管线 Path guard 权威职责，工具不重复实现
- ❌ 不得绕过 `fs` 直接 java.io 访问任意路径（越根读写禁止）
- ❌ 不得在未落盘时返回"写成功"（假成功 = 完成证据造假）
- ❌ 完成证据门不得被工具结果绕过（writeEvidence 只是证据输入，定案归 gate/check）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 读文件 | `FsReadTest`：读取内容+元数据，超限截断 |
| V2 | 写文件落盘 | `FsWriteTest`：写入后存在性+内容校验，emit write-evidence（writeEvidence 载荷含 paths/hash/turnId/ts） |
| V3 | 编辑 diff | `FsEditTest`：apply-patch 应用/回放 |
| V4 | 越根拒绝 | `FsPathEscapeTest`：`../` 与符号链接逃逸 → 管线 Path guard deny |
| V5 | 覆盖审批 | `FsOverwriteApprovalTest`：覆盖已有文件无审批 → APPROVAL_REQUIRED |
| V6 | 假成功拦截 | `FsFakeSuccessTest`：写失败仍报成功 → 无 writeEvidence，gate/check deny completed |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=Fs*Test"
```

## §7 影响域

- 作用范围：**每工具调用**（L3.6 工具集）+ **完成证据路径**（P0-a 输入）；被 Agent 循环、`gate`（证据门，经 write-evidence）、审计（写操作合规）消费。
- 故障影响半径：单工具不可用；写路径缺陷 = 证据完整性风险（V1-V6 为发布门）；升级 = 工具装卸无感。
- 升级影响：writeEvidence 载荷变更 = gate 消费方同步（05 §4 契约为 SSOT）。

## §8 后续（未纳入首版）

- 结构化 diff 预览（编辑前 show-diff）
- 大文件流式读写（spill 联动）与文件锁/并发写保护（多 Agent 协作）

---

*GNEX3.0 文件系统工具 — gnex3-cap-tool-fs SDD（审计修订版：统一写证据 + 管线/工具预检职责界定）。*
