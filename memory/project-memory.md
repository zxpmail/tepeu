# Project Memory — Tepeu（develop）

当前：**从零重写**（2026-09-07）。标本 `legacy/os-9/`。新库根 `tepeu/`。identity / syscall / persist / session / policy / dispatch / llm / execution / loop / commands / load+host / conformance 已过审；真模型刀已落待人审（2026-09-14），**产品双模式可真跑（默认 fake / -P real 真模型）**；下一刀未圈（/compact 或压缩外新面）。规划 [docs/rewrite-0.md](../docs/rewrite-0.md)。产品：单用户单机 CLI。

v1 工作台记忆在 [`docs/archive/v1/project-memory-v1.md`](../docs/archive/v1/project-memory-v1.md)。

## Tech stack（develop）

- Runtime: Java 21
- 规划模块：session / policy / persist（父 POM：api + sqlite 第一刀）/ dispatch / llm（网关统一调用，第一刀 fake 挂在网关后）/ execution / loop；共用类型 identity/syscall；测试套件 conformance
- host：`tepeu/host/`（2026-09-14 裁进 reactor）。Spring Boot 4.0.7 无 Web，POM 钉 4.0.7。POM 依赖 persist-sqlite、load；llm 实现走 profile——默认依赖 llm-fake（`llm.kind=fake`），`-P real` 依赖 llm-anthropic（`llm.kind=anthropic`），application.properties `tepeu.llm=@llm.kind@` 经 maven-resources `@` 分隔符过滤，单 jar 只背一份实现。装配收 `Assembly.wire → Wired`；后端经 `Backends.create(kind, …)` 反射缝（host 不 import 具体实现）。stdin REPL：`/` 行交 commands、普通行入收件箱跑一轮、终答读账印最后 ASSISTANT_MESSAGE、EOF 退出；stdout/stderr 钉 UTF-8。db `tepeu.db` 落当前目录，工作区参数缺省当前目录。env：`ANTHROPIC_AUTH_TOKEN` 必需（真模型，缺则一行报错 exit 2）、`ANTHROPIC_BASE_URL` 缺省官方、`ANTHROPIC_MODEL` 缺省 `claude-sonnet-4-6`
- 验证：`mvn -f tepeu/pom.xml test`。标本对照：`mvn -f legacy/os-9/os/pom.xml test`
- 持久化：`persist` 父 POM（api + sqlite 第一刀）。session/policy 只依赖 api。host 第一刀依赖 persist-sqlite、llm-fake
- `llm.*`：自研协议与传输。gateway `generate(log, question)`——问句非空白尾插 USER 不写账（/btw 面）；llm/anthropic 用 **Jackson 3**（`tools.jackson.*`，Spring Boot 4 BOM 钉 3.1.4；包名空间换了、异常 unchecked、`asString()`/`asLong(long)`），Jackson 2 BOM（2.21.4）别混。SYSTEM→`system` 字段，TOOL→user，同角色相邻合并。错误码 `LLM_HTTP_<n>` / `LLM_IO_ERROR`
- v1 工作台：`legacy/` / `main`（Spring Boot 4.0.7 + Spring AI 2.0.0 + React 18）

## Architecture（develop）

- 规划见 [`docs/rewrite-0.md`](../docs/rewrite-0.md)。标本 `legacy/os-9/`。
- `Product-Spec.md` 是 v1 档案（Forge 门要求留在根目录）

## 口径

规划以 [`docs/rewrite-0.md`](../docs/rewrite-0.md) 为准。`Product-Spec.md` 是 v1 档案。

吸收：`docs/legacy-absorption.md` · `docs/work-docs-absorption.md` · `docs/archive/reference/gnex3-reference.md` · `docs/archive/reference/agent-runtime-security-series.md`（九宫格对账；Gate=边界脚本；Observation 已开 jar；gate/response 空 jar 不预开）  
勿吸内核：`spacexp-structure.md`（结构标本，不吸 Spring starter）；`tencent-harness-engineering.md`（AI Coding）；`ai-eval-observability-pipeline.md`（评测可观测运维）；`terax-ai.md`（⑤ ADE 产品，不同线）；`grok-bot-reference.md`（⑤ Computer-Use + 重建 host；WAL/完成通道/`directionEpoch` 可扫，勿按 35 槽改组件；Router/Local Docker 是重建新增）；`maka-reference.md`（log-first 同线工作台；机制可扫，勿开 Graph/Eval jar，勿降级 `llm.*` 断言）；`genericagent-reference.md`（个人 Computer-Use + 技能自结晶；不同线，循环自报完成，不吸）；`goose-reference.md`（Rust 本机 Agent + MCP；状态=对话投影已有同形，不吸 AlwaysAllow / 调度器）；`deer-flow-reference.md`（LangGraph 超级 Agent 工作台；`/goal`+评估器当完成门，不吸）；`lifeos-reference.md`（个人意图层，骑在 CC 一类 harness 上；名字带 OS，不是内核）；`aios-reference.md`（学术「LLM as OS」；C4/C5/C6 已落，不因 HEAD 再开调度）；`osone-ai-reference.md`（Gemini+Tuya 家居 Jarvis；不同线）；`osone-reference.md`（Common-joeAI 愿景仓；叙事空壳，勿与前者混）；`earthwalker-agent-os-reference.md`（本机编码 harness；绿构建当完成，不进内核）；`openclaw-reference.md`（Gateway+频道助手；可信面/策略在代码已有同形，默认沙箱关不抄）

## Gotchas（develop 仍有效）

- 包管理器是 **Maven**，不是 Gradle。本机仓库常在 `D:\maven\repo`（非默认 `~/.m2`）
- 不要把 v1 `ChatModelFactory` / `@Tool` 装饰器路径抄进 `os/llm` 或任何内核组件
- 不要把新能力倒进「大包」；领域默认跟组件走。JDBC/方言只在 `persist`。组件 ≠ 插件（无 Ctx / 无热插）
- 系统提示装配归属未定（llm 或 loop）。控制循环不引用工具实现类。
- `identity`/`syscall` 是共用类型；`session` 是模块。真相=事件日志/用量流水/操作审计。不抽日志工具袋。不打 SQL / args / digest / 密钥 / 正文。被拒调用由 loop 记 `TOOL_RESULT` + `attrs.error` 短码（2026-09-13 裁，rewrite-0 §3.2）；行式诊断日志只归 host
- persist：session/policy 只依赖 api，不关库。host 选实现并负责生命周期。接口不含 JDBC / SQL / 对象存储 SDK。与 execution 工作区 I/O 不是同一套口。契约已钉（conformance）：append 撞键抛、put 就地覆盖不挪位、list 恒写入序、重开见全部已提交写。
- session 账本 seq 是**每本账独立编号、各从 1 起无空洞**（conformance 钉死，非全局 seq）；审批 consume 二次是 empty 不是抛；ask 未决幂等同单。
- session 五本账 seq、收件箱领取、policy approvalId 都是非原子读改写（`list().size()+1`），单进程单写者前提；loop 线程化前收口。`SessionId` 禁 `/`（identity 层构造器，2026-09-13 收紧）。
- policy 审批：ask 幂等靠 `approval-pending` 索引；persist 无删除，decide/consume 是覆盖写置章（decidedAt/consumedAt）；许可绑 argsDigest，严格单次。
- llm/gateway 只读事件日志派生模型可见序列（USER/ASSISTANT/TOOL_RESULT），不写事件、不记用量；assistant 事件由 loop 追加；接线归 load（`llm.generate` 参数留空、会话从 ctx 取）。
- 工具请求协议：模型输出首行 `@tool 名 k=v;k=v`（2026-09-14 裁），**参数分隔符是 `;` 不是空格**——`path=x.txt content=v` 会解析成单键（AssemblyTest 抓出过），解析在 loop/ToolRequest；工具轮 ≤8。handler 错误码与 dispatch 门五码分家（PATH_OUTSIDE_WORKSPACE / IO_ERROR / PROC_*）。spawn 只杀直接子进程，进程树杀灭随 OS 级隔离（§10 欠账）。
- host CLI 输出已钉 UTF-8（2026-09-14 真模型刀收口，`System.setOut/setErr`），管道中文不再 mojibake；legacy GBK 控制台需 `chcp 65001`。
- host 测试必须 **profile 中立**：不 import `com.tepeu.llm.fake.*` / `anthropic.*` 具体类（`-P real` 下 fake 类不在 classpath 会 NoClassDefFoundError）；要固定行为用匿名 `LlmBackend` lambda。`-pl host -am -P real` 依赖解析可能失败，全 reactor 跑即可。
- **本机 Agent OS 骨架可演示** ≠ 企业 OS / 完整 OS。execution 隔离程度如实报告。host 读密钥，os 库不读。
- 压缩改写 surface 后必须 bump `log.surfaceEpoch`，否则下一笔 `llm.generate` 会 ASSERTION
- `/approve` 只许本会话的 approvalId；审批许可绑 argsDigest，不单绑 syscall 名
- **禁止第二处宣布 completed**。证据在事件日志，宣判只一处。工具回报、模型文本、进程退出码都不是终态。
- 「OS」= 分层纪律，不是完整 OS。内核有 Inbox/claim，**没有调度器**。不为了更像 OS 开调度切片。不要改成「按 CC 模式做」；CC 只吸 ③ 机制
- 不要在 `legacy/` 加功能
- `Product-Spec` 七层 / 四智能体 / 记忆 P0 / WASM+V8 **不是** `os/` 现状
- **切先于填**（`.claude/skills/_shared/cut-before-fill.md`）：写码前切口清单或 `none`；新组件/端口/schema/完成权先问；第三份同构必须抽；人审只审切口。实现纪律「不写未来抽象」不得盖过这条。纹理对着 `.forge/project-taste.md` 的近邻，不另起组织法
- **结构标本 spaceXP**（[zxpmail/spaceXP](https://github.com/zxpmail/spaceXP)，`docs/archive/reference/spacexp-structure.md`）：学「一件事一个模块 + 模块内角色固定」。不吸 Spring starter / AOP / common-utils。不因对照重切已有根包。结构糊 = 泥球 = 日抛；土可以留，角色放错必须改。约束冻在 Maven / 角色档 / `PackageRoleTest`，不冻在提示词。不上微服务换墙。根包 `interface`/`enum` 不得 import `*.local`；`InMemory*` 不得进 `src/main`

