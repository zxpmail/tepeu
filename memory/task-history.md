# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-09-06 | 第一刀 | identity/syscall 切口 | 不合并。identity=谁/工作区/对话；syscall=信封、不依赖 identity。补 Usage；InvokeContext 不再写成「一次调用」 |
| 2026-09-06 | 迁标本 | os host → legacy/os-9 | 源码不含 target。根目录不再有 os/ host/ |
| 2026-09-06 | 人圈 | 落地三句 | 一个默认对话；读/问模型放行、写/跑先问；第一刀 fake |
| 2026-09-06 | llm | 网关统一调用 | gateway 是口；fake 挂在网关后。loop 只调网关 |
| 2026-09-06 | 目录 | os/ 四层 | types / persist / kernel / run。写入 rewrite-0 §7.1 |
| 2026-09-06 | 实现 | 第一刀各一份 | persist-sqlite、llm-fake。以后再加实现模块 |
| 2026-09-06 | host | 依赖那个用那个 | host POM 依赖哪个 persist/llm 实现，就加载哪个、用哪个 |
| 2026-09-06 | 依赖 | 只朝下 | persist 不知道 session；session 调用 persist-api。写入 rewrite-0 §8 |
| 2026-09-06 | 文档 | 活文档只写「是什么」 | rewrite-0 / foundation / texture / handoff / README 去掉否定对照 |
| 2026-09-06 | 目录名 | identity/syscall/conformance/orchestration/compose 是干嘛的 | 写入 rewrite-0 §7 与 foundation；后两个新树不留名 |
| 2026-09-06 | persist | 只定读写端口 | 不写死 SQLite/JDBC；文件或库、本机或远程由实现决定 |
| 2026-09-06 | 命名 | 不用 bus | 新树模块名 `dispatch`；方法 `invoke`；写入 rewrite-0 |
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
| 2026-09-02 | 日志防泥球 | 身份归 host，Persist 口不挂 name | 曾短暂加过 `jdbc(ds,name)`，已删；`HostPersist`；引擎成功行不重复 |
| 2026-09-02 | 运维日志 | 哑路径补必要日志 | 不新开组件；`System.Logger`；Loop 结局 / 总线拦截 / persist 生命周期 / CLI；不打正文与密钥 |
| 2026-09-02 | 切先于填 | 实现/审核纪律拆成切 vs 填 | 共享协议 `cut-before-fill.md`；第三次同构必须抽；人审只审切口；未改 `os/` |
| 2026-09-02 | spaceXP 结构 | 对照 zxpmail/spaceXP 收结构口味 | 学角色分包不学 starter；标本入 archive；未改 `os/` |
| 2026-09-02 | os 角色重切 | 本机实现进 `*.local`；根包只留端口/聚合/值 | 无新 jar；`InMemoryCapabilityBus`→`LocalCapabilityBus`；os+host 测试过 |
| 2026-08-29 | grok-bot 对账 | `C:\grok-bot-0.18-reconstructed-main` → `docs/archive/reference/grok-bot-reference.md` | ⑤ 不同线；机制可扫不立项；不新开 ADR；Router/Docker 是重建新增 |
