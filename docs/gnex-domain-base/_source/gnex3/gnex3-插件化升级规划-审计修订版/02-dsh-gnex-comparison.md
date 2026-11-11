# dsh ↔ GNEX 框架对比：机制对比表 + 分类映射表 + 映射图

> **状态**：设计定稿（v0.1）· 关联：00-PROJECT-TASKS.md · 01-gnex2.0-architecture.md · 03-fusion-design.md
> **立场**：对比不是排名。dsh 是个人/开发者本机 Agent runtime（Cordis 一切皆插件）；GNEX 是企业级多租户多副本 Agent 编排平台。融合 = 把 dsh 已验证的**组合语义**移植进 GNEX 的**企业骨架**（服务化、副本、弹性、租户全部保留）。

---

## 1. 机制对比表（为什么 dsh 改起来容易、gnex 改起来麻烦）

| 维度 | dsh（Cordis · TypeScript） | GNEX 2.0（Spring · Java） | 差距本质 | 融合方案（GNEX3.0） |
|------|---------------------------|---------------------------|----------|---------------------|
| 服务容器 | `ctx` 代理容器：get/provide/isolate/intercept | Spring DI（ApplicationContext + @Service），**编译期装配** | 容器语义够，缺"能力级动态组合" | cordis-jvm 内核：Ctx 服务容器（L1） |
| 能力声明 | 插件 `inject`（依赖声明，就绪才装载）/ `provide` | 类注解 + 构造器注入，**依赖图是隐式的** | 能力边界不可枚举/不可装卸 | Plugin.inject/provide 显式声明（L0/L1） |
| 生命周期 | Fiber：load/unload，effect 可逆回卷 | Spring Bean init/destroy，**无卸载回卷** | 改一个能力要动 Bean 图、重启进程 | Fiber + effect 回卷（L1） |
| 组合/配置 | profile/bundle/patch（cordis.patch.yml 组合行，id 覆盖） | Maven 模块 + application.yml，**能力无装配清单** | 加/改能力 = 改代码 + 改依赖 + 重新构建 | service-assembly.yml 装配清单（L0） |
| 事件 | waterfall（可拦截可否决）/ emit（通知），scope 过滤 | Spring ApplicationEvent（进程内）+ RocketMQ Bus（跨服务） | 进程内缺 waterfall/scope 语义 | L1 事件总线（waterfall/emit）+ 跨服务 Bus 保留 |
| 工具管线 | tools/pre-execute → guard → execute → post-execute → result | InspectorChain（Loop→Permission→Sandbox→Path→Risk→Security）+ ToolCallback | 管线在，但**不可插拔式扩展**（Inspector 顺序写死） | 融合：Inspector 链作为安全插件挂 pre-execute waterfall（L3） |
| 会话 | append-only SessionEvent + surface 投影 + compaction | agent_run_event + SessionLoop + 状态面 CQRS | 事件化方向一致，缺统一事件词汇 | 会话事件化对齐（session/event · session/flush · tool/call · tool/result） |
| 模型路由 | llm seam + 适配器注册（deepseek/pi-ai）+ retry | LLMConfigManager + spring-ai ChatClient | 适配器已有 DI，**无路由注册表语义** | 模型路由插件化（provider 注册表 + retry + default） |
| 沙箱 | sandbox seam + backend（bwrap/landlock/Windows-ACL） | SandboxRuntime（firecracker/opensandbox/local + pool + cluster） | gnex 更强（池/集群/弹性） | 保留 gnex 沙箱池；对齐 seam 语义（L3） |
| 审批 | approval seam + waterfall（缺席 deny）+ 待办 | InspectorChain 高风险弹窗 + AskUserQuestionTool | **不是原语**（有时弹有时豁免，难回放难列队） | 审批原语插件（入队/同turn回放/待办）🔒（P1 E 做真） |
| 作用域 | scope/realm（agent.ctx 按 agent 归档） | tenant/project/user Interceptor | 有隔离，无"作用域内能力组合" | Scope/Realm（租户/项目/会话）+ 会话装配 preset |
| 会话装配 | agent preset（per-session 组合文件） | Agent 定义 + Skill 加载 | 有装配，无"会话级组合片段" | agent-preset 插件（会话级组合） |
| 记忆 | 会话引用（弱）；无企业记忆平面 | Memory L0-L4 + 记忆 SoR 目标态（热温冷多副本） | gnex 目标更强 | 企业记忆 SoR 🔒（L4） |
| 客户端 | dsh.client 双面插件 + __DSH_BOOT__ | React SPA（d1-web）+ SSE | 前端无插件机制 | 前端模块化（先模块后插件） |
| 部署/弹性 | 本机单进程（127.0.0.1:3080） | k8s 多副本 + Master-Worker + 弹性伸缩 | gnex 独有 | 保留（L4 企业层） |
| 租户 | 无 | 租户/项目/用户隔离 + Worker 亲和 | gnex 独有 | 保留（L4，🔒） |
| 完成门 | turn 在 nothing owed 关闭（**非证据门**） | P0-a 完成=证据（委派 Write 硬闸已有，需统一） | gnex 独有且更严 | 完成证据门 🔒（L4，P0-a 硬约束继承） |

**结论（痛点根因）**：gnex 2.0 的"能力"是**代码模块**而非**组合单元**——加能力 = 改代码 + 改依赖 + 重新构建 + 重启；能力边界不可枚举、不可装卸、不可独立演进。dsh 的插件化把"能力"变成**组合行**（id 可覆盖、依赖可声明、生命周期可回卷、事件可插拔）。GNEX3.0 = gnex 企业骨架 + dsh 组合语义。

---

## 2. 分类映射表（dsh 15 组 186 插件 ↔ GNEX 模块）

> 每行 = dsh 分类 → gnex2.0 对应模块 → 融合去向（GNEX3.0 插件组）。详细到插件级的映射见 04 插件目录。

| dsh 分类（插件数） | GNEX 2.0 对应 | 差距 | 融合去向 |
|-------------------|---------------|------|----------|
| 启动与组合层（10） | 无对应 + gnex-dev-assembler + application.yml | **完全缺失**：gnex 无组合行/补丁机制 | L0 新增 6 插件（装配/内核/加载器/清单/命令/配置） |
| 核心服务层（12） | Spring 基础 + agent 注册 + PromptAssembly | 缺 ctx/事件/scope 语义 | L1 内核层 8 插件 |
| 模型路由层（5） | gnex-llm-adapt + LLMConfigManager | 适配器有，路由注册表无 | L3.1 模型路由 5 插件 |
| 会话与持久化层（15） | agent_run_event + SessionLoop + 状态面 | 方向一致，词汇未统一 | L3.2 会话 7 插件 + L4 状态面 |
| 存储与设置层（11） | gnex-persistence-common + credential + MinIO | 部分对应 | L3.3 存储/凭证/附件 5 插件 |
| 沙箱与文件系统层（12） | gnex-sandbox + SandboxRuntime | gnex 更强（池/集群） | L3.9 沙箱 4 插件（保留增强） |
| 进程与终端层（9） | ProcessTool + 沙箱执行 | 部分对应 | 并入 L3.6 工具 + L3.9 沙箱 |
| 工具注册表层（6） | ToolCallback + InspectorChain | 管线不可插拔 | L3.5 工具注册表/管线 3 插件 |
| 委派与工作流层（6） | Task→Worker + workflow 包 | 已有等价物 | L3.7 委派/工作流 3 插件 |
| 模型工具层（18） | FileSystemTools/GrepTool/AskUserQuestionTool/HttpClientTool… | 工具基本已有 | L3.6 工具插件集 12 插件 |
| 目标/计划/技能/审批层（12） | TodoWrite + Plan + SkillRegistry + Inspector 确认 | goal 缺失；审批非原语 | L3.8 目标/计划/技能/审批 6 插件 |
| 上下文与压缩层（7） | 上下文感知压缩（已有） | 缺 token 计量/修剪/溢出 | L3.10 上下文/压缩 3 插件 |
| 宿主服务与RPC层（21） | 各服务 Controller + SSE + Kong | 服务面已有 | L4 企业 API 网关 + 服务面保留 |
| 客户端运行时层（10） | d1-web（React/TS） | 前端无插件机制 | L5 前端内核（模块化） |
| 浏览器UI插件层（32） | d1-web 页面（12+ 入口） | 页面模块化已有 | L5 前端功能模块 |

### 2.1 映射结论

1. **同构可对齐（约 8 组）**：模型路由、会话、存储、工具、委派、目标/技能、上下文、客户端 —— dsh 分类与 gnex 模块一一对应，融合 = 对齐语义（事件词汇/接口）。
2. **gnex 独有（约 3 组）**：企业层（租户/多副本/记忆SoR/计费/审计/认证/总线/完成门）—— dsh 没有，**保留并插件化**（protected）。
3. **dsh 独有缺口（约 2 组）**：组合与装载（组合行/插件内核/生命周期）、浏览器插件化 —— gnex 缺失，**新增**（Java 移植）。

---

## 3. 映射图（文字版）

```
dsh 侧                                    GNEX 2.0 侧                      GNEX 3.0 侧
─────────────────────────────────────    ─────────────────────────────    ──────────────────────────────
[启动与组合层 10] ── 机制缺失 ──────────▶ [dev-assembler + yml]        ──▶ L0 组合与装载层(6) ★新增
[核心服务层 12]   ── 语义缺失 ──────────▶ [Spring DI + 注册服务]        ──▶ L1 内核层(8) ★新增
[模型路由层 5]    ── 对齐 ──────────────▶ [gnex-llm-adapt]             ──▶ L3.1 模型路由(5) 改造
[会话持久化 15]   ── 对齐 ──────────────▶ [SessionLoop+状态面]          ──▶ L3.2 会话(7)+L4 状态面 改造
[存储设置 11]     ── 对齐 ──────────────▶ [persistence-common+cred]     ──▶ L3.3 存储凭证(5) 改造
[沙箱文件 12]     ── gnex 更强 ─────────▶ [SandboxRuntime 池/集群]      ──▶ L3.9 沙箱(4) 保留增强
[工具注册表 6]    ── 管线融合 ──────────▶ [ToolCallback+InspectorChain] ──▶ L3.5 工具管线(3) 融合
[委派工作流 6]    ── 对齐 ──────────────▶ [Task→Worker+workflow]        ──▶ L3.7 委派(3) 改造
[模型工具 18]     ── 对齐 ──────────────▶ [FileSystemTools 等]          ──▶ L3.6 工具集(12) 改造
[目标技能审批 12] ── 对齐+缺口 ─────────▶ [Todo/Plan/Skill/Inspector]   ──▶ L3.8 目标技能审批(6) 改造
[上下文压缩 7]    ── 对齐 ──────────────▶ [上下文压缩(已有)]            ──▶ L3.10 上下文(3) 改造
[宿主服务RPC 21]  ── gnex 独有 ─────────▶ [各服务 Controller+Kong]      ──▶ L4 企业 API 网关 保留
[客户端 42]       ── 模块化 ────────────▶ [d1-web React SPA]            ──▶ L5 客户端层 模块化
                    ── gnex 独有 ────────▶ [9 服务 + k8s + 租户 + 记忆]  ──▶ L4 企业层(13) 保留+插件化
```

---

*待补：05 接口关系 · 06 路线图 · 交互式页面 architecture.html。*
