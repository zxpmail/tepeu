# GNEX3.0 插件目录（全部插件定义 · 映射 · 接口 · 影响域 · 选型）

> **状态**：审计修订版 v0.2（2026-08-18）· 关联：03 融合设计（修订版）· 审计/06
> **说明**：以新架构分层插件为视角。每个插件给出：与 dsh 的映射、与 gnex2.0 模块的映射、提供服务/注入服务、关键事件、技术选型（Java 移植 dsh / gnex2.0 改造 / 全新 / 中间件桥）、受保护级别、宿主服务、影响域。详细设计见 specs/<域>/ 对应 SDD。

## 图例

- **选型**：🅳=Java 移植 dsh 实现 · 🅶=gnex2.0 模块改造 · 🅽=全新实现 · 🅱=中间件桥
- **保护级别**：🔒platform=平台持有（禁租户/Agent 装卸）· 🔐admin=管理员可装卸 · 无=默认
- **宿主**：该插件装配在哪个 gnex 服务内运行（L2 服务层为部署载体）

---

## L0 组合与装载层（6）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-l0-assembly | 服务装配清单（service-assembly.yml 解析/校验/组合） | dsh-app-boot / dsh（profile·bundle·patch 组合） | gnex-dev-assembler + application.yml | 🅳 | - | 各服务启动期 |
| gnex3-l0-kernel | cordis-jvm 内核（ctx/Service/Plugin/Fiber/Events/Effect/Scope） | cordis（Context/Service/Registry/Events/Fiber） | 无（新增） | 🅳 | 🔒 | 各服务 |
| gnex3-l0-loader | 插件加载器（模块/装配清单装载、依赖等待、卸载回卷） | cordis-plugin-loader / cordis-plugin-include | 无（新增） | 🅳 | - | 各服务 |
| gnex3-l0-inventory | 插件清单与管理面（状态/版本/装卸 API） | dsh-host-plugin-inventory | registry-service 扩展 | 🅶 | 🔐 | registry-service |
| gnex3-l0-commands | 用户命令注册表（/命令） | dsh-commands + dsh-command-* | agent-service shell/ShellRouter | 🅶 | - | agent-service |
| gnex3-l0-config | 配置中心（settings 类比，含 secret 引用） | dsh-settings-file + dsh-credentials-local | LLMConfigManager / config 包 | 🅶 | - | 各服务 |

## L1 内核层（8）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-l1-ctx | Ctx 服务容器（get/provide/isolate/intercept） | cordis Context | Spring DI（ApplicationContext） | 🅳 | - | 各服务 |
| gnex3-l1-events | 事件总线（waterfall/emit + scope 过滤） | cordis events + dsh-scope | Spring ApplicationEvent + Bus | 🅳 | - | 各服务 |
| gnex3-l1-fiber | 插件生命周期（装载/卸载/effect 回卷） | cordis Fiber | Spring Bean 生命周期 | 🅳 | - | 各服务 |
| gnex3-l1-scope | 作用域（租户/项目/会话 realm 隔离） | dsh-scope | TenantInterceptor / ProjectScopeInterceptor | 🅶 | 🔒 | 各服务 |
| gnex3-l1-settings | 设置服务（插件配置段） | dsh-settings | @ConfigurationProperties | 🅶 | - | 各服务 |
| gnex3-l1-logger | 日志/诊断 | cordis logger | SLF4J/Logback | 🅶 | - | 各服务 |
| gnex3-l1-invariants | 运行时不变量注册表 | dsh-invariants | 无（新增） | 🅳 | - | 各服务 |
| gnex3-l1-prompt | 系统提示组装（人设/章宪/配方序） | dsh-system-prompt + dsh-persona + dsh-agent-instructions | agent-service PromptAssemblyLayers / BASE_AGENT_CHARTER | 🅶 | 🔒 | agent-service |

## L3 能力层（52）

### 3.1 模型路由（5）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-model-llm | LLM 服务 seam（provider 注册表/路由） | dsh-llm | gnex-llm-adapt + LLMConfigManager | 🅶 | - | agent-service |
| gnex3-cap-model-deepseek | DeepSeek 适配器 | dsh-llm-deepseek | spring-ai DeepSeek adapter | 🅶 | - | agent-service |
| gnex3-cap-model-retry | 请求重试/模型 fallback | dsh-llm-retry | 部分已有（OpenClaw P1 演进项） | 🅶 | - | agent-service |
| gnex3-cap-model-default | 默认模型选择 | dsh-agent-default-model | LLMConfigManager | 🅶 | - | agent-service |
| gnex3-cap-model-gateway | 模型网关（KV/预算/路由记录） | dsh-llm（prepareCall）+ dsh-token-meter | agent/model/gateway（已有） | 🅶 | - | agent-service |

### 3.2 会话与持久化（7）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-session-store | 会话日志（append-only 事件溯源） | dsh-session | agent_run_event + SessionLoop | 🅶 | - | agent-service |
| gnex3-cap-session-persist | 会话持久化（PG） | dsh-session-persistence(-jsonl) | gnex-agent-persistence | 🅶 | - | agent-service |
| gnex3-cap-session-query | 会话查询/搜索 | dsh-session-query | observability/query | 🅶 | - | observability-service |
| gnex3-cap-session-projection | 会话投影（CQRS 物化） | dsh-session-projection | state-plane projector | 🅶 | - | state-plane-service |
| gnex3-cap-session-checkpoint | 检查点（Redis） | dsh-session-checkpoint-policy | Redis checkpoint（已有） | 🅶 | - | agent-service |
| gnex3-cap-session-title | 会话标题 | dsh-session-title | 会话标题摘要（已有） | 🅶 | - | agent-service |
| gnex3-cap-session-export | 会话导出 | dsh-session-log-export | 无 | 🅶 | - | observability-service |

### 3.3 存储与凭证（5）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-storage | 存储 hub（backend 注册表 + domain） | dsh-storage + dsh-storage-domain | gnex-persistence-common | 🅳 | - | 各服务 |
| gnex3-cap-storage-pg | PG 后端 | dsh-storage-json | MyBatis-Plus 持久层 | 🅶 | - | 各服务 |
| gnex3-cap-credentials | 凭证管理（secret 引用） | dsh-credentials-local | registry/credential | 🅶 | 🔒 | registry-service |
| gnex3-cap-attachment | 附件/产物存储（MinIO） | dsh-attachment-local | MinIO（已有） | 🅶 | - | agent-service |
| gnex3-cap-workspace | 工作区注册表 | dsh-workspace | workspace 包 | 🅶 | - | agent-service |

### 3.4 Agent 循环（4）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-agent-registry | Agent 注册表 | dsh-agent | registry/agent + AgentRegistryService | 🅶 | - | registry-service |
| gnex3-cap-agent-loop | Agent 循环（turn/step 事件化） | dsh-agent-loop | agent/runtime ReActLoop + SessionLoop | 🅶 | - | agent-service |
| gnex3-cap-agent-preset | Agent 会话装配（preset） | dsh-agent-presets | AgentLoader 改造为装配器（D-P1-32） | 🅶 | - | agent-service |
| gnex3-cap-agent-inbox | 会话 inbox（steer/followup/inject） | dsh-agent（inbox）+ dsh-agent-tool-presentation | agent/runtime inbox | 🅶 | - | agent-service |

### 3.5 工具注册表与管线（3）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-tools | 工具注册表 + 执行管线（pre/guard/execute/post/result，含内置 guard：SandboxEnforcer/Path/Risk/Security） | dsh-tools | agent/tools + InspectorChain 融合（D-P1-07） | 🅶 | 🔒 | agent-service |
| gnex3-cap-tools-timeout | 工具调用超时策略 | dsh-tool-call-timeout-policy | 部分已有 | 🅶 | - | agent-service |
| gnex3-cap-tools-loop | 循环检测/重复提醒 | dsh-repeat-tool-reminder | LoopInspector（已有，改造） | 🅶 | - | agent-service |

### 3.6 模型工具（12）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-tool-bash | bash/进程工具 | dsh-tool-bash(+persistent) | ProcessTool | 🅶 | - | agent-service |
| gnex3-cap-tool-pwsh | PowerShell 工具 | dsh-tool-pwsh | 无 | 🅳 | - | agent-service |
| gnex3-cap-tool-fs | 文件系统工具（read/write/edit） | dsh-tool-fs | FileSystemTools + ClaudeWorkspaceWriteTool | 🅶 | - | agent-service |
| gnex3-cap-tool-fs-search | 搜索工具（glob/grep） | dsh-tool-fs-search | GrepTool + GlobTool | 🅶 | - | agent-service |
| gnex3-cap-tool-web | Web 搜索/抓取 | dsh-tool-web + dsh-web-search-deepseek | WebSearch（已有） | 🅶 | - | agent-service |
| gnex3-cap-tool-todo | 任务清单工具 | dsh-tool-todo | TodoWriteTool | 🅶 | - | agent-service |
| gnex3-cap-tool-ask-user | 用户提问工具 | dsh-tool-ask-user | AskUserQuestionTool | 🅶 | - | agent-service |
| gnex3-cap-tool-goal | 目标工具 | dsh-tool-goal | GoalService（agent.service，已有；改造为事件化 goal/change） | 🅳 | - | agent-service |
| gnex3-cap-tool-jobs | 后台任务工具 | dsh-tool-jobs | 无 | 🅳 | - | agent-service |
| gnex3-cap-tool-str-replace | 文本替换编辑器 | dsh-tool-str-replace-editor | ClaudeWorkspaceWriteTool | 🅶 | - | agent-service |
| gnex3-cap-tool-mcp | MCP 工具（管线包装） | dsh-mcp-client | McpToolRuntimePort 包装（连接管理留 registry，D-P1-10） | 🅶 | - | agent-service（消费）/registry-service（连接） |
| gnex3-cap-tool-http | HTTP 工具 | 无 | HttpClientTool（已有） | 🅶 | - | agent-service |

### 3.7 委派与工作流（3）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-subagents | 子代理注册表 | dsh-subagent | Task→Worker（Coordinator） | 🅶 | - | agent-service |
| gnex3-cap-subagent-backends | spawn/fork 后端 | dsh-subagent-spawn/fork-in-process | 子 Agent 调度（已有） | 🅶 | - | agent-service |
| gnex3-cap-workflow | 工作流引擎 | dsh-workflow + dsh-workflow-worker-thread | workflow 包（已有） | 🅶 | - | agent-service |

### 3.8 目标/计划/技能/审批（6）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-goal | 目标领域 | dsh-goal + dsh-goal-round-driver | 无（新增） | 🅳 | - | agent-service |
| gnex3-cap-plan | 计划模式 | dsh-plan-mode | Plan 页面/流程（已有部分） | 🅶 | - | agent-service |
| gnex3-cap-schedule | 定时调度 | dsh-schedule | 定时任务（部分） | 🅶 | - | agent-service |
| gnex3-cap-skills | 技能注册表 | dsh-skill + dsh-skill-filesystem | SkillRegistry（已有） | 🅶 | - | registry-service |
| gnex3-cap-approval | 审批原语（入队/回放/待办） | dsh-user-approval | InspectorChain 高风险确认（改造为原语） | 🅶 | 🔒 | agent-service |
| gnex3-cap-permission | 权限预设 | dsh-permission-presets | RBAC（auth-service） | 🅶 | 🔒 | auth-service |

### 3.9 沙箱与执行（4）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-sandbox | 沙箱 seam（执行世界抽象） | dsh-sandbox + dsh-fs + dsh-shell | gnex-sandbox（已有抽象） | 🅶 | - | sandbox-runtime |
| gnex3-cap-sandbox-backends | 沙箱提供方（firecracker/opensandbox/local） | 无（dsh 用 bwrap/landlock） | provider/*（保留） | 🅶 | - | sandbox-runtime |
| gnex3-cap-sandbox-pool | 沙箱池/集群/弹性 | 无 | pool/cluster（保留） | 🅶 | - | sandbox-runtime |
| gnex3-cap-sandbox-policy | 沙箱裁决器（档位/网络/配额，管线 guard） | dsh-sandbox-policy | SandboxEnforcer 收敛为管线 guard（D-P0-01） | 🅶 | 🔒 | agent-service（裁决） |

### 3.10 上下文与压缩（3）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-cap-compaction | 上下文压缩（token 驱动 + LLM 摘要） | dsh-compaction(-basic) + dsh-token-meter | 上下文感知压缩（已有，改造） | 🅶 | - | agent-service |
| gnex3-cap-pruner | 工具结果修剪 | dsh-compaction-tool-result-pruner | 无 | 🅳 | - | agent-service |
| gnex3-cap-spill | 溢出存储 | dsh-spill(-local/policy) | 无 | 🅳 | - | agent-service |

## L4 企业层（13）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-ent-tenant | 租户边界（硬过滤+亲和调度） | 无 | TenantInterceptor + Worker label 亲和 | 🅶 | 🔒 | 各服务 |
| gnex3-ent-gate | 完成证据门（P0-a 统一出口） | 无（dsh 无此原语） | DelegationDeliverableResolver + gates | 🅶 | 🔒 | agent-service |
| gnex3-ent-memory | 企业记忆 SoR（热温冷多副本） | dsh-session-reference（弱类比） | 记忆平面（目标态） | 🅽 | 🔒 | state-plane + platform |
| gnex3-ent-memory-providers | 记忆提供方（L0-L4 语义层） | 无 | Memory 系统改造 | 🅶 | 🔒 | agent-service |
| gnex3-ent-ha | 多副本/弹性/心跳/重调度 | 无 | WorkerRegistry + HealthMonitor + k8s | 🅶 | - | platform/registry |
| gnex3-ent-billing | 计费/配额 | 无 | ops/billing | 🅶 | - | ops-service |
| gnex3-ent-org | 组织/成员/角色 | 无 | ops/org + auth/rbac | 🅶 | 🔒 | ops + auth |
| gnex3-ent-audit | 审计日志 | 无 | platform/audit | 🅶 | 🔒 | platform-service |
| gnex3-ent-observability | 可观测（指标/链路/告警） | dsh-session-telemetry-otel | observability-service | 🅶 | - | observability-service |
| gnex3-ent-stateplane | 状态面（CQRS/projector/restore） | dsh-session-projection-cache | state-plane-service | 🅶 | - | state-plane-service |
| gnex3-ent-auth | 认证授权（SSO/RBAC/APIKey/JWT） | 无 | auth-service | 🅶 | 🔒 | auth-service |
| gnex3-ent-bus | 消息总线（RocketMQ 可靠性） | 无 | bus-service | 🅶 | - | bus-service |
| gnex3-ent-api | 跨服务 API 网关（REST/gRPC/SSE） | dsh-host-apiproxy + dsh-client-connection | 各服务 Controller + Kong（微服务规格） | 🅶 | - | 各服务 |

## L5 客户端层（2+模块）

| 插件 ID | 名称 | 对标 dsh | 对标 gnex2.0 | 选型 | 保护 | 宿主 |
|---------|------|----------|--------------|------|------|------|
| gnex3-client-kernel | 前端内核（路由/状态/SSE 解析） | dsh-client-web + dsh-client-runtime | d1-web frontend 内核 | 🅶 | - | 前端 |
| gnex3-client-modules | 前端功能模块（会话/工具/运营/审计/设置…） | dsh-client-ui-*（32 个） | d1-web 页面（12+ 入口） | 🅶 | - | 前端 |

---

## 统计

| 层 | 插件数 | 备注 |
|----|--------|------|
| L0 组合与装载 | 6 | 新增 3（内核/加载器/装配） |
| L1 内核层 | 8 | 新增 3（ctx/events/fiber Java 版） |
| L3 能力层 | 52 | 改造 45 · 移植 7 |
| L4 企业层 | 13 | 保留 12 · 新增 1（记忆 SoR） |
| L5 客户端层 | 2 | 前端模块化 |
| **合计** | **81** | 含 16 个受保护（15 🔒platform + 1 🔐admin） |

> 注：插件数含子插件（如工具插件集 12 个、记忆提供方）；独立 SDD 以本目录为准，一个插件一份 spec。

*待补：05 接口关系（服务目录/事件）· 06 路线图 · specs/ 各插件 SDD。*
