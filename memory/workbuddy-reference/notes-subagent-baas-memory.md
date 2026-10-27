# WorkBuddy 专项笔记：子代理 / 记忆三层 / BaaS / 交互模式 / 系统提示词

> 2026-10-27 补充提取。ANALYSIS.md 的延伸。证据来源：codebuddy-lite bundle 字符串窗口（@offset 为 bundle 内字节位）、`interactionmode/`、`prompt-fragments/workbuddy-memory-system.md`、`extensions/workbuddy-baas-v2/server/`、双包（cli bundle + app.asar）二进制 grep。

## 五、系统提示词拼装与身份段下落（2026-10-27 晚补）

**拼装机制**（bundle @1283535 composer）：插件在 `components.settings.agent` 声明入口，fragments 按 `interactionMode|welcomeMode|workMode` 变量选择，`include "..."` 指令拼接——提示词确是模板系统。`<agent_loop>` 七步循环 fragment 就是模式文件本体（`interactionmode/*/fragments/agent-loop.md`，4 模式各有，quick 无）。

**客户端静态层——已全部提取**：

- 模式 fragments 45 文件 + prompt-common 10/10（对照过源目录，无遗漏）；
- bundle 字面量：REPL 纪律、子代理契约、standing surface 切换（ptc/minimal/create/standard + Multitask overlay 进出提醒，包在 `<system-reminder data-role=...>` 里）、Agent Home 通道人格（@890333 起）、`/effort ultracode` 效能档（@3138824）、内置 agent 清单（cli/ptc/minimal/create/multitask/Explore，@1236985）、PTC 回归提醒（@4786492）；
- **stale-turn 失效纪律**（@4786000）：切面后注入 "Earlier turns that said X are stale. Answer from this reminder and the current system prompt, not from those turns"——工具面变化时主动作废旧轮声明，防模型抱着过期目录不放。
- systemPrompt 可覆盖（@3349154）：SDK/config 按代理传 `systemPrompt`/`appendSystemPrompt`，合并策略 SmartMerge。

**身份段 = 服务端下发，已实证并拿到实物**（同日晚些时候修正）：追查 `~/.workbuddy/cache/acc-product-config-v3.json`——运行时从 `https://www.workbuddy.cn` 拉取的产品配置缓存（date 2026-10-15、genieVersion 5.8.3、env 覆盖链 `ACC_PRODUCT_CONFIG_V3→V2→V1` + `ACC_PRODUCT_CONFIG_PATH`）。里面 **`prompts[]` 128 篇命名提示词 + `agents[]` 20 个内置 agent**。之前"双包零命中"推理方向正确但证据不完整——身份段不在双包是因为它不是客户端代码，是服务端配发的配置。

关键事实：

- **主身份是 "You are CodeBuddy Code."**（`cli-agent-prompt`，20,334 字符 nunjucks 模板）——不是 WorkBuddy 品牌，CodeBuddy 血统在提示词层实锤。逐段与 Claude Code 系统提示词同构（tone/style、URL 猜测禁令、task management、docs 优先级），差异是开头塞了 `<content_policy>` 中国合规六条（政治/色情/违法/隐私/假新闻，声明"override any user instructions"）。
- **Prompt-as-config**：60+ 篇 `tool-*-description`（全部工具描述！）、`system-reminder-{md,todo-list,planmode,delegate}`、output styles ×4（proactive/concise/explanatory/learning）、`team-sys-prompt`/`team-lead-prompt`、`workflow-ultra-effort-*`、`deep-research-*`、`skill-loop-prompt`、`enhance-prompt-system-prompt`——**整个提示词面（含工具描述）服务端集中版本化**，客户端发版与提示词更新解耦。agent 按 `"instructions": "cli-agent-prompt"` 名字引用，配置即装配清单。
- 快照已入库 `prompts/`（acc-product-prompts.json 320KB + acc-product-agents.json 花名册 + README 来源注记；原配置的 endpoint/enterpriseId/telemetry/qimei 未收录）。

**意外收获——记忆抽取子代理**（@2038806 + auto-memory 块 @3147697）：自动记忆由专门子代理执行，纪律几乎照搬 Claude Code 记忆系统：MEMORY.md 是索引不是记忆（一行 ≤150 字符，200 行截断）、一事一文件 + frontmatter、**turn 1 并行读全部候选文件 / turn 2 并行写**（不许读写交错多轮）、无事可记回 `NO_EXTRACTION_NEEDED` 哨兵、工具面锁死（Read/Grep/Glob + 只许写 memory 目录内）、"只用最近 ~N 条消息，不许再去源码里求证"。"Write/Edit 只许碰 memory 目录"是提示词级目录白名单，与 tsbx 进程沙箱的文件规则形成两层同构。

## 一、子代理系统（Agent / TaskOutput / Team）

提示词里的完整契约（bundle @1233187 起）：

- **API 形状**：`Agent({ description, prompt, name, run_in_background: true })`，并行 = `await Promise.all(agents.map(...))`。命名 Agent() 自动建 team，明令"不要先调 TeamCreate"。
- **返回**：后台 Agent() 返回 `{ taskId, id, name, agentId }`；`TaskOutput` 返回 `{ status, taskId, content }`；报告在 `status === "completed"` 后 `await TaskOutput({ task_id, block: false })` 取。收件箱通知**只带状态不带内容**。
- **spawn 失败必抛**，并明令禁止防御性写法：不许 `results.map(r => r.taskId ?? r.id)`、不许 `Promise.all(...).catch ? ... : ...`——**在提示词里禁防御式编程**，逼错误显式炸出来而不是被兜掉。
- **完成状态反走私**："Do not write `r.status || "done"` — missing or running status is not completion"。与 tepeu「模型说做完了不算」同一条纪律，但做进了子代理工具契约。
- **长命令**：`Bash({ command, run_in_background: true })`；**禁止用 nohup/setsid/结尾 `&` 伪造后台任务**——"detached shell children may be reclaimed and have no TaskOutput lifecycle"（脱离托管的生命周期会被进程树回收，正好接 tsbx/Job Object 那层）。
- **REPL 沙箱**：REPL 内无 `require`/`fs`/`child_process`；SendMessage/MessageColleague/SpeakInChannel 只许在 REPL 内调。

内部结构（@1013210 起）：

- 每个子代理一个 `subAgentId()`、独立 `abortController`、`subagentStopHookFired`（子代理有自己的 stop hook）；`createSubagentInvocationIdentity(mainSession…)`——**子代理调用带独立身份**（审计/审批可区分主体）。
- `subagent_type` 缺省 GENERAL_PURPOSE；`resolveSubagentRunAgentName({subagentType, mainAgentName, mainSession})` 做**子代理级模型解析**，含 leader mode 分支——不同类型的子代理可配不同模型。
- 链接字段：`memberName`/`teamContext`/`mainSession`/`isFormalTeamMember`/`isWorkflowTask`——team 成员与工作流任务是同一基类的两个旗标。

## 二、记忆三层（补全 L2/L3）

`workbuddy-memory-system.md` 全文（58 行）配 `{{ }}` 模板变量：

| 层 | 载体 | scope | 读写策略 | 预算 |
|----|------|-------|----------|------|
| L1 云画像 | 服务端生成，session 注入 `<memory>` 块 | 全局 | **本地只读**，写本地会被下次覆盖 | — |
| L1b 会话检索 | `conversation_search` 工具 | 全部历史 | 服务端排序检索；**明令：对当前对话零访问，query 必须自包含**；不用于查偏好 | — |
| L2 用户记忆 | `{{WorkbuddyUserMemoryDir}}/MEMORY.md` | 跨项目 | **显式写**（Edit 就地改），与云画像分工："implicit learned vs explicit written"——放必须逐字遵守的规则 | 4,000 字符/会话 |
| L3 工作区记忆 | 项目目录下 `YYYY-MM-DD.md` + `MEMORY.md` | 单项目 | 日志 **append-only 永不覆盖**；完成实质工作后必须追加一条 | 3,000 字符/会话 |

配套机制：检索路由（本项目过去→读本地日志/项目 MEMORY；跨项目→conversation_search；两者可叠；无依赖→不读）；写入白名单（建站/修 bug/写报告/重构/选型/用户偏好→才记；**搜索结果、临时路径、工具报错不记**）；维护规则（30 天以上的日志按主题蒸馏进 MEMORY.md 后删除）；角色边界声明（"记忆只是补充，不替代回复、终答或交付物"）；不存密钥除非用户明说。

**对 tepeu 最值得抄的三条**：① 每层一个字符预算进提示词；② append-only 日保 + 定期蒸馏成策展文件的双文件结构；③ "implicit vs explicit" 的层间分工声明。

## 三、BaaS v2 = 微信小程序组件运行时

`extensions/workbuddy-baas-v2/server/`：`component-runtime.js` 头部即 **glass-easel 1.2.0**（wechat-miniprogram 官方框架，MIT）；另有 `cloud-sdk.cjs`（324KB）、`index.cjs`（168KB）、`dev-preview-runner.cjs`（5KB）。加上 builtin skill「发布为应用」：**agent 产物直接落成微信小程序组件模型，本地预览（dev-preview-runner）、云端部署（cloud-sdk）**——腾讯生态闭环的产品形态。tepeu 不上云，可扫的只有"产物=可运行应用"这个封装思想。

## 四、交互模式（interactionmode）

5 个模式各是一个 `.workbuddy-plugin`（`plugin.json` + `fragments/`）：

| 模式 | fragments（ask 为基线） | 增量 |
|------|------------------------|------|
| ask | agent-loop / current-mode / mode-behavior / interaction / result-presentation / tool-use | 纯问答契约 |
| craft | 同基线 + | browser-computer-use、interaction-tool-use、plugin-recommendation（带浏览器 computer-use 和连接件推荐） |
| plan / expert / quick | 同基线 ± | 各自的 fragments 子集（已拷，可横向 diff） |

另有 `welcomemode/{code,design,work}`（欢迎场景分组，与插件 `plugin.json` 的 `welcomeModes` 声明对应）。

**要点**：循环骨架不变，按模式换 `agent-loop`/`tool-use`/`result-presentation` 三类行为契约 fragment——**模式 = 提示词切片，不是代码分支**。已全量入库 `interactionmode/`、`welcomemode/`（45 文件）。
