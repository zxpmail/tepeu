# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-09-14 | conformance | 契约套件刀，欠账清完 | 钉死：seq 每本账独立从 1 起（非全局）；put 就地覆盖不挪位；consume 二次 empty 非抛；ask 未决幂等；审批绑 argsDigest。kill -9 故障注入（子 JVM 强杀 WAL 恢复）。单写者前提进 session/policy javadoc。夹具三份留（人裁）。契约测试 0 实现 bug；107 绿 20 模块 |
| 2026-09-14 | load+host 合刀 | 装配 + 进程入口，产品第一次可真跑 | host 落 tepeu/host/ 进 reactor；系统提示走网关（构造器收 systemPrompt、Role.SYSTEM 头插）；Assembly.wire→Wired 一个记录；db=当前目录 tepeu.db、工作区=参数缺省当前目录、不读密钥；@tool 分隔符是 `;` 不是空格（测试抓出）；全树 78 绿 19 模块，真跑+重启持久化已验 |
| 2026-09-14 | commands | 斜杠命令表刀 | /approve 只收本会话 approvalId、已决拒、决策写 audit；/status 只读账不打正文；/btw 仅 idle、经 dispatch 按名调 llm.generate、用量进流水不写日志；问句参数 gateway 第一刀忽略；不依赖 run 层实现模块 |
| 2026-09-14 | execution+loop 合刀 | run 层收口，产品主形状立起 | @tool 首行协议（解析归 loop，不合式按答复）；工具轮上限 8；spawn 30s 超时；handler 码与门五码分家；Role.TOOL 进可见序列；destroyForcibly 后 waitFor；只杀直接子进程记欠账（OS 级隔离随标本 WindowsJob 类做） |
| 2026-09-13 | 可观测裁定 | 生产日志口径 | 详尽日志=三本账+host 诊断层，内核不撒行式日志；被拒调用复用 TOOL_RESULT + attrs.error（不扩词汇）；host 挂 slf4j；persist kill -9 故障注入列入收口期。写入 rewrite-0 §3.2 |
| 2026-09-13 | llm | run/llm gateway+fake 刀 | gateway 只读不写：visible 纯函数第一刀只放 USER/ASSISTANT_MESSAGE，assistant 事件归 loop 追加；结果与用量透传后端；fake 固定文本 Usage(1,1)；新增 run 聚合层（tepeu-run → tepeu-llm） |
| 2026-09-13 | dispatch 契约审计 | 全合成裁决收口 | 名字空白/policy 抛/审批通道抛一律合成 DENIED（fail-closed）；handler 回 null 合成 HANDLER_ERROR；ArgDigest 加长度前缀去歧义；失败类型/seq 语义/put-list 序归 conformance 刀 |
| 2026-09-13 | dispatch | kernel/dispatch 刀 | 全合成结果不抛异常（人裁，弃 os-9 拦截走异常）；错误码 CANCELLED/DENIED/APPROVAL_REQUIRED/NOT_FOUND/HANDLER_ERROR；NEED_APPROVAL 先 consume 后 ask（反序会孤立 decide）；未装配按 DENIED；测试自带 FakeApprovalStore 不复制 persist 夹具 |
| 2026-09-13 | policy | kernel/policy 刀 + session 收口 | 三值裁决 + 审批单次许可；approvalId 走序号簿（哈希在冻结时钟下撞键）；persist 无删除，决策/消费覆盖置章；SessionId 禁 `/`（identity 层）；open 不一致 fail fast；types 收紧 Usage 禁负、failure 必带码 |
| 2026-09-13 | session 人审 | 审计 + 收口 | Log/Ledger/Audit 抽 `appendDated`/`readBook`（第三同构必须抽）；删死 null 检查；open 语义与 `/` 校验留 policy 前；单写者前提记档 |
| 2026-09-07 | session | 事件 attrs | SessionLog.append 可带 Map，空合法。落库前缀 a.。其余审查项先留 |
| 2026-09-07 | 第一刀 | session | kernel/session。五本账走 persist-api。无 fork / 压缩 / 列表。DEFAULT 一个对话 |
| 2026-09-07 | persist-sqlite | 关库 / UNIQUE / WAL | close 置位+checkpoint；UNIQUE 认错误码不扫文本；WAL+busy_timeout=5000 |
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
