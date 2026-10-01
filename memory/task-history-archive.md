# Task History Archive

> One row per session id. Written UTF-8 by compact-task-history.py.

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-08-29 | grok-bot §9 | 工具集三层 offered≠registered；挂账等 tool_use | 第十轮已裁 tools 序未落；无发现口禁并回全表；未改 os/ |
| 2026-08-31 | maka 对账 | apache/maka → `docs/archive/reference/maka-reference.md` | log-first 同线；完成/压缩/恢复可扫；不立项；不降级断言 |
| 2026-08-31 | maka §7 | checkpoint coverage × surfaceEpoch | 世代≠源哈希；切点不拆对；checkpoint 留 entries；挂账不写码 |
| 2026-08-31 | maka 补吸收 | 晚到终态 / 缺 RESULT / SECURITY 句式出 archive | 进 §8.5 + gap + project-memory；未改 os/ |
| 2026-08-31 | maka §8 | 读 runtime/core：权限 / 延迟工具 / loop-gate | 无新吸收项；DoomLoop≠只闸失败；不抄隐藏嵌套 |
| 2026-08-31 | 不对账吸收 | 撤 Grok/Maka 进底板的挂账与活文档注入 | 对照留 archive；第十轮 tools 序已够 |
| 2026-09-02 | 日志防泥球 | 身份归 host，Persist 口不挂 name | 曾短暂加过 `jdbc(ds,name)`，已删；`HostPersist`；引擎成功行不重复 |
| 2026-09-02 | 运维日志 | 哑路径补必要日志 | 不新开组件；`System.Logger`；Loop 结局 / 总线拦截 / persist 生命周期 / CLI；不打正文与密钥 |
| 2026-09-02 | 切先于填 | 实现/审核纪律拆成切 vs 填 | 共享协议 `cut-before-fill.md`；第三次同构必须抽；人审只审切口；未改 `os/` |
| 2026-09-02 | spaceXP 结构 | 对照 zxpmail/spaceXP 收结构口味 | 学角色分包不学 starter；标本入 archive；未改 `os/` |
| 2026-09-02 | os 角色重切 | 本机实现进 `*.local`；根包只留端口/聚合/值 | 无新 jar；`InMemoryCapabilityBus`→`LocalCapabilityBus`；os+host 测试过 |
| 2026-08-29 | grok-bot 对账 | `C:\grok-bot-0.18-reconstructed-main` → `docs/archive/reference/grok-bot-reference.md` | ⑤ 不同线；机制可扫不立项；不新开 ADR；Router/Docker 是重建新增 |
| 2026-09-06 | 从零重写 | 停止旧树修补；规划阶段 | `docs/rewrite-0.md`；`os/` `host/` 冻结；S1 改为从零；源码待迁 `legacy/os-9/` |
| 2026-09-06 | 结构说明收紧 | 方向≠现状；session不止记下；一门只管点名 | `docs/tepeu-foundation.html`；并法标成一种切法；不改代码 |
| 2026-09-06 | 结构说明改人话 | 去掉钢筋/司机/进路等黑话 | `docs/tepeu-foundation.html`；还没定稿；不改代码 |
| 2026-09-06 | 结构说明 | 按那份 html 章节画七个文件夹 | 核心=记下+先问；orchestration/compose 以后并；未定不改代码 |
| 2026-09-06 | 收 os-9 | 九盒结构像 v1 一样进 archive | `docs/archive/os-9/`；代码不搬；活文档回 texture 表一 |
| 2026-09-06 | R1 像 XP | observation 并进 llm | 不新 jar；`Observation.view` 现属 llm；删 tepeu-os-observation |
| 2026-09-05 | S0 认 | 从头做 tepeu，不空仓 | 产品=本机账+门；CLI 已是产品；S2 invoke 未圈 |
| 2026-09-05 | 进路 1 /status | Slash 只读账，不经模型 | `StatusCommand`：entries 类型 + loop.state + 未决审批；不打正文；`/tasks` 等进路 4 |
| 2026-09-05 | 钢筋机器门 | 约束冻在测试，不冻在提示词 | `PackageRoleTest`：根包契约禁 local import；InMemory* 禁 main；不新开 ArchUnit / 微服务 |
| 2026-09-02 | host 读 CC Switch | 无 env 时用当前 Claude 供应商 | `ANTHROPIC_AUTH_TOKEN`；认 settings.json id；不进 os/；不打 key |
| 2026-09-02 | 审查收口 | 根口倒依赖 / 关库所有权 / 日志短码 | `prepare`→`LlmTransports`；`detect`→`OsJails`；Wired 不关库；finish 不打 SQL；`SqliteDataSources`/`MemoryAssembly` 名未动 |
