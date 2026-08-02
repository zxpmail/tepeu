# Tepeu v0.2.0 — Harness（Product-Spec §9 Phase 2）

> 功能冻结说明日期: 2026-07-18  
> **发布状态（2026-08-02 复核）**：源码与版本号 `0.2.0` 已对齐；发布说明齐全。  
> **本机 `docker build`**：⏳ 当前开发机无 Docker CLI，镜像定义已就绪，有守护进程后须补验。  
> **正式 git tag / GitHub Release**：仍可选（尚未打 tag）。  
> 本文仅覆盖 **DEV-PLAN Phase 5–9** / Spec **M2.1–M2.5**。主线后续切片（自主调度等）见 `DEV-PLAN.md` Phase 10+，**不在 v0.2.0 功能冻结范围内**。

---

## 概述

v0.2.0 在个人工作台（v0.1）之上交付 **Harness 能力**：工具审批网、多 Agent 协作、MCP 桥接、成本预算与门禁。

**核心理念不变：模型是 CPU，Tepeu 是操作系统。**

---

## 相对 v0.1.0 的新增能力

### M2.3 Hook 安全网（DEV-PLAN Phase 5）

| 功能 | 状态 |
|------|------|
| `run_command` / `mcp_*` / 未知工具需批准；工作区 `write_file` 免批 | ✅ |
| 灾难性 shell（如 `rm -rf /`）直接 DENY | ✅ |
| SSE 审批条；阻塞等待或批准后续跑；超时清条 | ✅ |
| 会话授权按「工具+参数」 | ✅ |
| REST 写/删/上传/恢复 + 终端 WS 走 `HostChannelGuard` | ✅ |
| 幻觉门禁（写父目录 + 声称路径扫描） | ✅ |
| 本机实例令牌 `X-Tepeu-Token`（审批与危险宿主 API） | ✅ |

**已知限制**：实例令牌为本地单机防护，非完整多用户登录；幻觉扫描为启发式正则。

### M2.1 多 Agent（DEV-PLAN Phase 6）

| 功能 | 状态 |
|------|------|
| Planner → Implementer → Reviewer 流水线 | ✅ |
| Goal + 验收标准契约 | ✅ |
| `POST /api/multi-agent/stream` + 多 Agent 面板 | ✅ |
| Implementer/Reviewer 共享工具与 Hook；面板可批准 | ✅ |
| Reviewer `VERDICT` 取最后一行匹配；失败可见 | ✅ |
| 角色提示可配置覆盖；回合费用入账；可打开会话 | ✅ |

### M2.2 MCP（DEV-PLAN Phase 7）

| 功能 | 状态 |
|------|------|
| `spring-ai-starter-mcp-client` 依赖 | ✅ |
| `McpToolBridge` 并入 ChatService 工具链（`mcp_<server>_<tool>`） | ✅ |
| 资源列表 + `POST /api/mcp/resources/read` | ✅ |
| `GET /api/mcp/status`、服务商页 MCP 区块 | ✅ |
| 默认 `spring.ai.mcp.client.enabled=false` | ✅ |
| 自主调度会话对 MCP **不免批** | ✅ |

启用：合并 `mcp-servers.example.yml`（Spring 配置，非 Claude Desktop JSON），设 `enabled=true`。  
真实 stdio/SSE server 联调依赖本机 Node/`npx` 等，未在 CI 默认跑。

### M2.4 成本仪表盘（DEV-PLAN Phase 8）

| 功能 | 状态 |
|------|------|
| 工作区累计用量 + 预算条 | ✅ |
| 告警阈值（默认 80%） | ✅ |
| 可选硬门禁（超预算阻断 chat / multi-agent） | ✅ |
| 左侧「成本」面板 | ✅ |

### M2.5 发布（DEV-PLAN Phase 9）

| 交付物 | 状态 |
|--------|------|
| 本发布说明 | ✅ |
| Docker 多阶段构建定义（JAVA_OPTS / healthcheck curl） | ✅ |
| 本机构建校验（`mvn package` + `npm run build`） | ✅ |
| 根目录 `README.md` 运行入口 | ✅（2026-08-02 补） |
| 本机 `docker build` | ⏳ 环境无 Docker CLI（`Dockerfile` / compose 已就绪；有 CLI 后执行并回写本表） |
| git tag / GitHub Release | ⏳ 可选（未打） |

---

## 技术栈（与 v0.1 相同基线 + MCP）

| 层 | 技术 | 版本 |
|---|------|------|
| 运行时 | Java | 21 LTS |
| 后端 | Spring Boot | 4.0.7 |
| AI | Spring AI | 2.0.0（含 MCP Client） |
| 数据库 | SQLite WAL | — |
| 前端 | React 18 + TS 5 + Tailwind 4 + Vite 6 | — |
| 部署 | Docker 多阶段 | — |

应用版本号：`0.2.0`（`backend/pom.xml` / `frontend/package.json`）。

---

## 快速开始

```bash
# Docker
docker build -t tepeu:v0.2.0 .
docker compose up -d
# http://localhost:30141

# 本地
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

---

## 已知限制（v0.2.0）

| # | 限制 | 说明 |
|---|------|------|
| 1 | 无完整登录 | 本机实例令牌防护危险 API（非多用户 OAuth） |
| 2 | 幻觉扫描启发式 | 正则匹配「已写入」类声明，非模型级校验 |
| 3 | ToolCallback deprecated | ADR-007，装饰器路径暂保留 |
| 4 | Crypto 明文 passthrough | ADR-006，遗留 key 重存即加密 |
| 5 | MCP 默认关闭 | 需自行配置 server |
| 6 | 无 CI/CD | 手动构建 |
| 7 | 正式 GitHub Release 可选 | 仓库：https://github.com/zxpmail/tepeu |

---

## 路线图（v0.2.0 之后）

- Spec §9 Phase 3 / DEV-PLAN Phase 10+：自主 Agent、多端、市场、WASM、v1.0 等  
- **不在 v0.2.0 冻结范围**；当前主线进度以 `CONTEXT.md` / `DEV-PLAN.md` 为准（可能已领先本说明）

---

## 升级说明

- 从 v0.1.0：SQLite 自动建 `workspace_budget` 表；其余表兼容。
- 建议备份 `tepeu.db` 与 `~/.tepeu/master.key` 后再升级。
