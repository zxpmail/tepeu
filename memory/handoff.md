# Handoff — Tepeu（从零重写）

> 到达后：本文件 → `CONTEXT.md` → [`docs/rewrite-0.md`](../docs/rewrite-0.md)。  
> 标本：`legacy/os-9/`。新库根 `tepeu/`。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 v1 档案。

**Last updated**: 2026-09-14（conformance 过审 `a89c435`；真模型刀已落待人审）

## 当前阶段

- 标本已迁：`legacy/os-9/os/`、`legacy/os-9/host/`。
- 已写已推：`identity`、`syscall`、`persist-api`、`persist-sqlite`、`session`、`policy`、`dispatch`、`llm`、`execution`、`loop`、`commands`、`load` + `host`、`conformance`（均人审过）。
- 2026-09-14 已裁：最小工具请求协议 `@tool`；工具轮上限 8；spawn 超时 30s；handler 级错误码与门五码分家；gateway `Role.TOOL`；系统提示走网关；host 进 reactor；`Assembly.wire → Wired`；conformance 全域 + 故障注入；夹具三份留；**真模型刀**：Anthropic Messages API + jackson3 + `/btw` 问句面 + Maven profile 切换。
- 纪律：一个组件写完，人审查通过才能继续。下一刀未圈（`/compact`，或压缩外新面）。

## 本刀 真模型（llm/anthropic + host profile）

- **gateway 问句面**：`generate(SessionLog, String question)` 单方法（旧单参签名删除）。问句非空白作 `LlmMessage(Role.USER, …)` 尾插可见序列，不写事件日志；Loop 传 `Map.of()` → null，无感。Assembly handler 改 `syscall.args().get("question")`（commands 本就传 `Map.of("question", q)`，零改动）。gateway 4→6 测试。
- **llm/anthropic**：包 `com.tepeu.llm.anthropic`，`AnthropicBackend implements LlmBackend`。SYSTEM→`system` 字段；USER/TOOL→`user`、ASSISTANT→`assistant`，同角色相邻合并 `\n\n`；`/v1/messages`，头 `x-api-key` + `Authorization: Bearer` + `anthropic-version: 2023-06-01`；JDK HttpClient 60s 不重试；`max_tokens=4096`（自裁，待审）。非 2xx 合成 `LLM_HTTP_<n>`、网络异常 `LLM_IO_ERROR`，不回显响应体。纯函数 `buildRequest`/`parseResponse`（text 块相连、其余块忽略、usage 透传），HTTP 路径不测（裁定）。5 测试。
- **jackson 3**（裁定照 Spring Boot 4.0.7 BOM）：Spring Boot 4 管的是 **Jackson 3 BOM 3.1.4**（`tools.jackson.core:jackson-databind`，包命名空间换了；Jackson 2 兼容 BOM 钉 2.21.4 别用）。API 实测：`asString()`/`asLong(long)`/`path()`/`JsonMapper.builder()`，异常 unchecked。版本钉根 POM `jackson.version`。
- **host profile 切换**：host POM 两 profile——默认（activeByDefault）依赖 `tepeu-llm-fake`、`llm.kind=fake`；`real` 依赖 `tepeu-llm-anthropic`、`llm.kind=anthropic`。application.properties 写 `tepeu.llm=@llm.kind@`，maven-resources `@` 分隔符过滤（`<configuration>` 归 resources-plugin 不归 `<resource>`，错位会 Malformed POM）。单 jar 只背一份实现（real jar 实测：含 anthropic 不含 fake）。
- **Backends 反射缝**：`create(kind, token, baseUrl, model)` 按名反射造后端，host 不 import 具体实现，两 profile 都能编译。env：`ANTHROPIC_AUTH_TOKEN` 必需（缺 → 一行 stderr + exit 2，无栈）；`ANTHROPIC_BASE_URL` 缺省官方；`ANTHROPIC_MODEL` 缺省 `claude-sonnet-4-6`。
- **UTF-8 收口**：`main` 里 `System.setOut/setErr` 钉 UTF-8——管道中文 mojibake 已消（真跑验证）。
- **真跑已验（real jar）**：主轮对话、`/btw` 问句（答出账里暗号"苹果"，问句确实进可见序列）、缺 token 报错 exit 2。本机 env 恰有 `ANTHROPIC_AUTH_TOKEN`，全链路真模型通。
- **教训**：host 测试必须 profile 中立——ReplTest 曾 `new FakeBackend()`，`-P real` 下 NoClassDefFoundError（全树 real 跑抓出）；改匿名 lambda backend。`-pl host -am -P real test` 在依赖解析处失败是 reactor 子集解析问题，全 reactor 跑即可。
- 测试：anthropic 5、gateway 6、BackendsTest 2；**全树 116 绿 21 模块（default 与 real 双 profile 同绿）**。

## 上一刀 conformance

- 路径 `tepeu/conformance`（纯 test 模块，无 main 源码）。对**接口**钉语义，任何实现都得满足。
- **persist 契约（8）**：append 撞键 IllegalStateException；put 新键尾插、旧键**就地覆盖不挪位**；list 恒按写入序；space 互不相通；空白 space 拒；关库后一切操作拒；重开见全部已提交写。
- **session 契约（8）**：**seq 每本账独立编号、各从 1 起、无空洞**（刀中钉死的语义，非全局 seq）；跨重连续号；open 主人/工作区不一致 fail fast；收件箱 NOW→NEXT→LATER、租约（TTL 300s）过期可重领、ack 后不领、nack 归队；寄存器覆盖写。
- **approval 契约（6）**：ask 未决幂等同单；decide 一次（二次抛）；consume 取走即消费（二次 empty）；消费后再调重新审批（新 id）；拒绝决策同样单次；许可绑 argsDigest——同名字不同参数无决策。
- **dispatch 契约（5，真栈）**：放行到 handler；审批全流程（APPROVAL_REQUIRED → decide → 放行一次 → 再调重新审批）；未登记名矩阵 DENY 不进查表；策略放行但未注册 NOT_FOUND；CANCELLED 先于一切。
- **故障注入（1）**：子 JVM 提交 10 条后 READY 挂住，父进程 destroyForcibly（TerminateProcess / kill -9 语义）→ 重开库 WAL 恢复，已提交写全活。
- **javadoc 收口**：`PersistedSession` / `PersistedApprovalStore` 写入单写者前提（非原子读改写，线程化前收口）。
- 契约测试抓出 0 个实现 bug；两处失败都是测试预期错（全局 seq 想当然）——修正后语义记档。
- 验证：全树 **107 绿，20 模块**。

## 上一刀 load + host（合刀）

- **gateway 扩面**：`LlmGateway(backend, systemPrompt)`，`visible(systemPrompt, events)` 头插 `Role.SYSTEM`（null/blank 无提示行）。
- **load**：`tepeu/load`（根包 `com.tepeu.load`）。依赖四层 + commands + persist-api + llm-gateway；**不依赖实现模块**（persist/backend 由 host 构造交入）。`Assembly.wire(persist, backend, systemPrompt, workspaceRoot) → Wired(session, dispatch, commands, loop)`：open 默认会话（主人 u / 工作区 ws）、PersistedApprovalStore、DefaultRuleMatrix、登记 `llm.generate` + execution 四 handler、`SYSTEM_PROMPT`（身份 + 工作区边界 + `@tool` 协议说明）。
- **host**：`tepeu/host`，Spring Boot 4.0.7 无 Web（banner off、root=WARN）。stdin REPL：`/` 行交 commands、普通行入收件箱 + `loop.runOnce`、终答**读账**印最后一笔 ASSISTANT_MESSAGE、EOF 退出。db=`tepeu.db` 落当前目录；工作区=首个参数缺省当前目录；第一刀不读密钥。spring-boot-maven-plugin repackage 出可执行 jar。
- **切口裁定（2026-09-14）**：host 进 reactor（`mvn -f tepeu/pom.xml test` 不变）；系统提示走网关非 loop；装配收 `Wired` 一个记录；路径默认当前目录。
- **真跑已验**：管道喂 `你好` / `/help` / `/status` 全对；二次启动读到首跑的 2 条事件 + 用量（进程退出记录仍在）。管道里中文是 mojibake（Java 按本地编码出，GBK 控制台正常）——编码收口归真模型刀。
- **教训**：`@tool` 参数分隔符是 `;` 不是空格——`path=x.txt content=v` 会被解析成单键 `path="x.txt content=v"`（AssemblyTest 抓出，LoopTest 同步改正）。
- **自审修正（2026-09-14，人审前）**：Repl 曾整账找最后一条 ASSISTANT_MESSAGE——本轮超限/失败没新终答时会把**上一轮旧答复**当本轮答案；改为本轮下标快照、只认本轮终答（回归测试：旧答复只许印一次）。banner 曾印身份 `ws`，Wired 现带 `workspaceRoot` 印真实路径。`visible` 空白提示归一不插 SYSTEM 行。
- 测试：load 3 绿（全名注册、端到端带提示、写文件审批回路 sqlite 真栈）、host 4 绿（含旧答复回归）、gateway 4 绿。
- 验证：`mvn -f tepeu/pom.xml test`（全树 **79 绿，19 模块**）；重打包真跑 banner 印真实工作区路径。

## 上一刀 commands

- 路径 `tepeu/commands`（根包 `com.tepeu.commands`）。依赖 identity、syscall、session、policy、dispatch；不依赖 run 层实现模块。
- `Commands(approvals, dispatch)` + `execute(Session, line)` → 印给操作者的文本；输入错误回用法，不抛。
- 四条：`/help` 表；`/approve <id>` 只收本会话 approvalId（异会话拒、已决拒、决策后写 audit）；`/status` 只读账（类型计数、loop.state、用量、本会话未决审批，不打正文）；`/btw <问>` 经 dispatch 按名调 `llm.generate`（仅 idle，用量进流水，不写事件日志）。未知命令回表。
- 问句参数第一刀被 gateway 忽略（fake 固定文本）；真模型刀再定传问句形制。
- 测试：6 绿（sqlite 真栈：审批三分支、账本摘要不打正文、btw 空闲门 + 失败透传）。
- 验证：`mvn -f tepeu/pom.xml test`（全树 71 绿，18 模块）。

## 上一刀 execution + loop（合刀）

- **execution**：包 `com.tepeu.execution`，`Workspace`（root 由 load 注入）。四名对齐矩阵：`execution.fs.read` / `fs.write` / `proc.spawn` / `sandbox.probe`；方法签名对齐 dispatch.Handler，装配直接方法引用。
- 路径圈禁 = resolve+normalize 限 root 内；不追符号链接、无 OS 级沙箱，probe 如实报告。spawn 30s 超时 destroyForcibly + waitFor；**只杀直接子进程**（孙进程占目录，见下欠账）。
- **handler 码分家（2026-09-14 裁）**：门五码归 dispatch；handler 自报 `PATH_OUTSIDE_WORKSPACE` / `IO_ERROR` / `COMMAND_MISSING` / `PROC_TIMEOUT` / `PROC_EXIT_<n>`。
- **loop**：包 `com.tepeu.loop`。`Loop.runOnce(Session)`：领取 → USER_MESSAGE → llm.generate → `@tool` 工具轮（≤8，超限记 REASONING）→ 终答 ASSISTANT_MESSAGE。失败调用（含 llm.generate 自身）记 `TOOL_RESULT` + `attrs.error`。`loop.state` 寄存器 running/idle；用量进流水；`Loop.complete(SessionLog)` 单一判定点。
- **最小工具请求协议（2026-09-14 裁）**：输出首行 `@tool 名 k=v;k=v` 即工具请求，其余为最终答复；不合式按答复处理；解析归 `ToolRequest`。
- gateway 扩量：`Role.TOOL`，TOOL_RESULT 进可见序列（回炉后模型看得见工具结果）。
- 测试：execution 5 绿（含超时杀进程）、loop 7 绿（sqlite 真栈端到端：工具轮、DENIED 记账、轮次上限、空队列）。
- 验证：`mvn -f tepeu/pom.xml test`（全树 65 绿，17 模块）。
- **欠账**：进程树杀灭（标本 WindowsJob/bwrap 的活）随 OS 级隔离做，rewrite-0 §10 记档。

## 上一刀 llm（gateway+fake）

- 路径 `tepeu/run/llm/`（gateway + fake），父 POM 链 run → llm。gateway 依赖 session、syscall；不知道 dispatch / host。fake 只依赖 gateway、syscall。
- gateway 公开：`LlmGateway`（generate(SessionLog)）+ `visible` 纯函数（事件日志 → 模型可见序列）+ `LlmBackend`（实现口）+ `LlmMessage`/`Role`。
- **gateway 只读不写**：不追加事件、不记用量；assistant 事件由 loop 追加（§5 步骤归 loop）。结果与用量透传后端返回值。
- 可见词汇（2026-09-14 随 execution/loop 刀扩）：`USER_MESSAGE`/`ASSISTANT_MESSAGE`/`TOOL_RESULT` 按日志序；TOOL_CALL/REASONING/PLAN_STEP 不可见。
- fake：离线固定文本 `FakeBackend.REPLY`，固定 Usage(1,1)。
- 接线（load 落地时）：`dispatch.register("llm.generate", (ctx, s) -> gateway.generate(session.log()))`，参数留空、会话从 ctx 取。
- 测试：gateway 3 绿 + fake 1 绿。
- 验证：`mvn -f tepeu/pom.xml test`（全树 53 绿，15 模块）

## 上一刀 dispatch

- 包 `com.tepeu.dispatch`。路径 `tepeu/kernel/dispatch`。依赖 identity、syscall、policy。不依赖 session / persist，不写日志。
- 公开：`Dispatch`（register / names 字典序 / invoke）+ `Handler`（函数接口）。
- **全部合成结果（2026-09-13 裁）**：不抛异常。invoke 序：取消 → 授权 → 查表 → 调用。错误码 `CANCELLED` / `DENIED` / `APPROVAL_REQUIRED` / `NOT_FOUND` / `HANDLER_ERROR`。
- 未装配 Policy 或审批通道按 DENIED（fail-closed）；策略返回 null 也 DENIED。NEED_APPROVAL **先 consume 后 ask**——顺序反了会把 decide 孤立掉。APPROVAL_REQUIRED 的 output 是 approvalId。handler 抛 RuntimeException 合成 HANDLER_ERROR（output 只带异常类名，不带 message 防泄漏）。
- **契约审计收口（2026-09-13）**：名字空白、策略抛错、审批通道抛错也合成 DENIED（原先会穿门抛异常）；handler 返回 null 合成 HANDLER_ERROR；`ArgDigest` 键值带长度前缀，`{"a":"b\nc=d"}` 不再与两键混淆。契约其余缺口（失败类型点名、seq 语义、put-后-list 序、单写者前提进 javadoc、保留码归属）归 conformance 刀。
- 测试：dispatch 12 绿（8+4），syscall 6 绿（5+1）。自带 FakeApprovalStore（未复制第三份 InMemoryPersist）。
- 验证：`mvn -f tepeu/pom.xml test`（全树 49 绿）

## dispatch 刀遗留（人审可裁）

- policy 的 `PolicyDeniedException` / `ApprovalRequiredException` 在全合成裁决下已无消费者（dispatch 不抛、loop 看结果）——policy 人审时定删或留。

## 上一刀 policy

- 包 `com.tepeu.policy`。路径 `tepeu/kernel/policy`。依赖 identity、syscall、persist-api。不关库。
- 公开：`PolicyVerdict`（封闭三值 ALLOW/DENY/NEED_APPROVAL）、`PolicyHook`（`evaluate(InvokeContext, Syscall)`）、`ApprovalStore`（ask 幂等 / decide 一次 / consume 取走即消费 / get / records）、`ApprovalRecord`、`PolicyDeniedException`、`ApprovalRequiredException`。
- 矩阵：`local/DefaultRuleMatrix`。`llm.*`、`execution.fs.read`、`execution.sandbox.probe` 放行；`execution.fs.write`、`execution.proc.*` 需批准；未登记 DENY。精确名 override 优先；`parse` 读 rules 文本。
- 适配：`com.tepeu.policy.persist.PersistedApprovalStore`。space `approval`（键 `ap-<seq>` 序号簿）+ `approval-pending`（会话+名称+指纹摘要索引，指向最新）。persist 无删除：决策与消费覆盖写置章（decidedAt / consumedAt）。审批绑 argsDigest，不单绑名称。
- 未做：dispatch 接线、`/approve` 命令、卫兵组合、标本的 SensitivePath/SensitiveCommand（第一刀不迁）。
- 验证：`mvn -f tepeu/pom.xml -pl kernel/policy -am test`（policy 11 绿；全树 36 绿）

## 已定（2026-09-13，可观测）

- 详尽日志 = 三本账写全 + host 一层诊断日志；内核组件不打行式日志（不打正文/args/密钥的纪律不变）。
- 被拒调用尝试：loop 记 `TOOL_RESULT` + `attrs.error` 短码，body 只带名；不新增事件词汇。
- host 落地时挂 slf4j（装配/启动/异常栈）；persist 补 kill -9 故障注入测试（归 conformance 或 loop 收口期）。
- 全文见 rewrite-0 §3.2。

## 已定（2026-09-13，session 遗留收口）

- `SessionStore.open` 不辨新建/读回；owner/workspace 与库里不一致 fail fast（IllegalStateException）。
- `SessionId` 禁 `/`，收在 identity 层构造器。
- session 五本账与 policy approvalId 的序号分配都是非原子读改写（`list().size()+1`），单进程单写者前提，loop 线程化前收口。

## 已定（2026-09-06 人圈）

- 库根 `tepeu/`。四层：`types/` · `persist/` · `kernel/` · `run/`。
- `load/`：装配。注入实现、拼系统提示、登记斜杠。
- `commands/`：斜杠表。第一刀 `/help` `/approve` `/status` `/btw`。`/compact` 以后加。
- `/btw`：旁问。看当前对话，不写事件日志，不调工具。用量进流水。第一刀仅 loop 空闲时。
- host：Spring Boot（无 Web）。`/` 行交给 commands。
- persist 第一刀 sqlite；llm 网关 + fake。host POM 依赖哪个用哪个。
- 依赖只朝下。
- 第一刀一个默认对话。默认授权：问模型/读文件放行；写文件/跑命令先问。
- 第一刀 llm 是 fake。真模型换 host POM。
- 第一刀不做：invoke、定时入队、`/compact`、`/btw` 并发、会话列表、`kernel/memory/`。
