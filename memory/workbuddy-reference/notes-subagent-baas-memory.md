# WorkBuddy 专项笔记：子代理 / 记忆三层 / BaaS / 交互模式

> 2026-10-27 补充提取。ANALYSIS.md 的延伸，四块小件。证据来源：codebuddy-lite bundle 字符串窗口（@offset 为 bundle 内字节位）、`interactionmode/`、`prompt-fragments/workbuddy-memory-system.md`、`extensions/workbuddy-baas-v2/server/`。

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
