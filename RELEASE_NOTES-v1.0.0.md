# Tepeu v1.0.0 — Agentic OS（Product-Spec §9 Phase 3 / M3.5）

> 功能冻结说明日期: 2026-08-07  
> **发布状态**：源码与版本号 `1.0.0` 已对齐；本说明覆盖 **DEV-PLAN Phase 10–17** / Spec **M3.1–M3.4** 及发布收口 **M3.5**。  
> **本机 `docker build`**：⏳ 当前开发机无 Docker CLI，镜像定义已就绪（`Dockerfile` / `docker-compose.yml`），有守护进程后须补验。  
> **正式 git tag / GitHub Release**：⏳ 可选（须批准后执行，尚未打 tag）。

---

## 概述

v1.0.0 在 Harness（v0.2）之上交付 **Agentic OS 主线能力**：自主调度、细粒度工具、文件/任务事件、Slash 命令、多端布局、应用市场、技能脚本沙箱。

**核心理念不变：模型是 CPU，Tepeu 是操作系统。**

相对 [v0.2.0](./RELEASE_NOTES-v0.2.0.md) 的增量见下文；v0.1 / v0.2 能力仍包含在本版本中。

---

## 相对 v0.2.0 的新增能力（Phase 10–17）

### M3.1 自主 Agent 调度（DEV-PLAN Phase 10）

| 功能 | 状态 |
|------|------|
| `agent_schedule` 表 + 定时/间隔调度 | ✅ |
| 「自主」面板：创建/启停/删除 | ✅ |
| 到期自动开会话并跑一轮；终态可见 | ✅ |
| 卡死 RUNNING 自动恢复 | ✅ |

**已知限制**：Spec「Hands 能力包」未做（超出定时调度切片）。

### 工具分类细化（DEV-PLAN Phase 11）

| 功能 | 状态 |
|------|------|
| 细粒度 `*Tool`（list/read/write/delete/search/run/read_output） | ✅ |
| `search_files`、`read_output` + 会话级命令输出缓存 | ✅ |
| Hook 按 `toolKind` 审批 | ✅ |

### 文件变更通知（DEV-PLAN Phase 12）

| 功能 | 状态 |
|------|------|
| `FileWatcherService` + `GET /api/events` SSE | ✅ |
| 前端按工作区过滤刷新；多 tab 共享连接 | ✅ |
| 设计：ADR-012 | ✅ |

### 后台任务通知（DEV-PLAN Phase 13）

| 功能 | 状态 |
|------|------|
| 独立 `GET /api/task-events` SSE（ADR-013） | ✅ |
| 通知铃 + 打开对应会话 | ✅ |

### Slash 命令框架（DEV-PLAN Phase 14）

| 功能 | 状态 |
|------|------|
| `/help` `/tasks` `/schedule` `/compact` `/status`（不经 LLM） | ✅ |
| 前端候选 + 手打拦截；`/compact` 清服务器历史 | ✅ |

### M3.4 多端适配（DEV-PLAN Phase 15）

| 功能 | 状态 |
|------|------|
| 断点 767px：侧栏抽屉、预览全屏、44px 触控（ADR-014） | ✅ |
| 移动冒烟 e2e（375×667） | ✅ |

### M3.3 应用市场（DEV-PLAN Phase 16）

| 功能 | 状态 |
|------|------|
| 内置目录 + 本机 ReqForge 扫描 + 可选远程清单 | ✅ |
| 「市场」面板一键安装；`install_source` / `install_version` | ✅ |
| 离线可装内置示例技能 | ✅ |

### M3.2 技能脚本运行时（DEV-PLAN Phase 17）

| 功能 | 状态 |
|------|------|
| GraalJS 沙箱（`js-community` 24.2.1）；ADR-015 | ✅ |
| `run_skill_script`（demo / `/scripts/*.js`）；限时强制中断 | ✅ |
| 隔离边界单测 | ✅ |
| 原生 WASM（wasmtime） | ⏳ 延后（ADR-015） |

### M3.5 本发布收口（DEV-PLAN Phase 18）

| 交付物 | 状态 |
|--------|------|
| 本发布说明 | ✅ |
| 版本号 `1.0.0`（pom / package.json / SetupWizard） | ✅ |
| Docker 定义校验（文件存在、多阶段结构） | ✅ |
| 本机 `docker build` | ⏳ 无 Docker CLI |
| Spec §10 基线记录（见下表） | ✅（能测则测，其余标明） |
| git tag / GitHub Release | ⏳ 可选（须批准） |

---

## Spec §10 成功指标 — v1.0.0 基线

| 指标 | Phase 3 目标 | v1.0.0 基线 | 说明 |
|------|-------------|-------------|------|
| Agent 冷启动时间 | < 10ms | 未测 | 无专项压测；启动为 JVM 进程级，非 Isolate 级 10ms |
| 单机并发 Agent 数 | > 100 | 未测 | 无压力测试环境；架构为虚线程 + 单机 SQLite |
| 记忆检索准确率 | > 90% | 部分 | FTS5 + 单测覆盖路径；无人工评估集 |
| 用户上手时间 | < 5 分钟 | 未测 | SetupWizard + 欢迎流具备；无正式用户测试 |
| 开源贡献者 / Stars / Docker 下载 | 社区目标 | N/A | 发布后 6 个月跟踪；本机无 Docker Hub 发布 |
| 任务完成率（succeeded） | > 85% | 未测 | Task 表可统计；无生产样本 |
| 7 日留存 / 周活 workspace | 社区目标 | 未测 | 无遥测管线 |

> 上表为诚实基线：工程能力已交付，**性能与社区类指标不在本切片伪造数字**。

---

## 技术栈（v1.0.0）

| 层 | 技术 | 版本 |
|---|------|------|
| 运行时 | Java | 21 LTS |
| 后端 | Spring Boot | 4.0.7 |
| AI | Spring AI | 2.0.0（含 MCP Client） |
| 脚本沙箱 | GraalJS Polyglot | 24.2.1（community） |
| 数据库 | SQLite WAL | — |
| 前端 | React 18 + TS 5 + Tailwind 4 + Vite 6 | — |
| 部署 | Docker 多阶段 | 定义就绪，本机未实测 build |

应用版本号：`1.0.0`（`backend/pom.xml` / `frontend/package.json`）。

---

## 快速开始

```bash
# Docker（有 CLI 时）
docker build -t tepeu:v1.0.0 .
docker compose up -d
# http://localhost:30141

# 本地
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

---

## 已知限制（v1.0.0）

| # | 限制 | 说明 |
|---|------|------|
| 1 | 无完整登录 | 本机实例令牌防护危险 API（非多用户 OAuth） |
| 2 | 原生 WASM 未落地 | 技能脚本为 GraalJS；见 ADR-015 |
| 3 | Docker 镜像未在本机实测 | 无 Docker CLI；定义见根目录 `Dockerfile` |
| 4 | 无 CI/CD | 手动构建与测试 |
| 5 | ToolCallback deprecated | ADR-007 |
| 6 | Crypto 明文 passthrough | ADR-006，遗留 key 重存即加密 |
| 7 | MCP / Hands 能力包 | MCP 默认关；Hands 未做 |
| 8 | Spec §10 性能与社区指标 | 见基线表，多数未测 |
| 9 | 正式 GitHub Release 可选 | 仓库：https://github.com/zxpmail/tepeu |

---

## 升级说明

- 从 v0.2.0：SQLite 自动迁移新增列（如 `skill.install_source` / `install_version`、`agent_schedule` 等）；兼容旧库。
- 建议备份 `tepeu.db` 与 `~/.tepeu/master.key` 后再升级。
- GraalJS 依赖随后端 JAR 引入；首次构建需拉取 Maven 坐标 `org.graalvm.polyglot:*:24.2.1`。

---

## Docker 定义校验（2026-08-07）

| 项 | 结果 |
|----|------|
| 根目录 `Dockerfile` 多阶段（node → maven → JRE 21） | ✅ 存在 |
| `docker-compose.yml` 端口 30141、卷挂载 | ✅ 存在 |
| 本机 `docker` CLI | ❌ 未安装 / 不在 PATH |
| `docker build` / `compose up` 实测 | ⏳ 暂缓 |

---

## 路线图（v1.0.0 之后）

- 集群高可用、完整 OAuth、原生 WASM 技能包、CI/CD、正式遥测与 Spec §10 压测补齐  
- Hands 能力包与社区运营指标另立版本跟踪
