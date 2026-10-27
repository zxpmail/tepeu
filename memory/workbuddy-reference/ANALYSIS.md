# WorkBuddy 连接件与 Skill 体系逆向分析

> 2026-10-27 提取。身份：`@genie/workbuddy-desktop` v5.8.3（Electron 37.x），Tencent Technology (Shenzhen)，`workbuddy@tencent.com`，www.workbuddy.cn，内部代号 **genie**。agent 运行时 = **codebuddy-lite**（`cli/dist/codebuddy-lite-wb.mjs`，9.4MB bundle）；连接件目录标记 `.codebuddy-connector`，与 CodeBuddy 同源。提取方法与产物索引见 [README.md](README.md)。内容版权归腾讯，仅限内部研究。

## 一、一句话结论

WorkBuddy 把"扩展"拆成三个正交生态——**skill（知识包，Anthropic SKILL.md 格式）、connector（远端 MCP 服务 + 配套使用知识打包）、expert（人格化专家包）**——全部走市场分发；运行时则收拢成 **PTC/Code Mode：REPL 是模型唯一直接工具，其余工具（文件/shell/搜索/MCP/子代理）全部活在 REPL 沙箱内，skill 文档随 REPL 的 `code` 参数注入**；计费/授权等硬门做在 PreToolUse hook + 声明式清单上，bypass 模式也绕不过。

## 二、三套扩展生态

| 生态 | 载体 | 分发 | 数量（本机实测） |
|------|------|------|------|
| skill | `SKILL.md` + references/scripts/assets | 市场 + builtin + 用户自装 | builtin 19 + userSettings 24 + plugin 1（`.skill-list-cache.json`） |
| connector | `mcp.json` + `skills/`（MCP 服务声明 + 配套 SKILL.md） | 官方目录 CDN（etag/sha 锚定） | 目录 443 / 本机物化 316 |
| expert | 专家包（agent/team 两型，展示字段 + references + scripts） | builtin + 市场 | expert-manager skill 管全生命周期 |

三生态各有自举 skill 维护：`skill-creator`（教 agent 写 skill，frontmatter 必须带 `agent_created: true` 供 `skill_manage` 回管）、`marketplace-skill-installer`（`search_and_install_skills` 一句话安装）、`recommend-connectors`（`search_plugins` + `suggest_plugin_install` 卡片推荐）、`expert-manager`（专家包转化/合规/批量更新）。**生态的运营流程本身也是 skill**——扩展系统自举，不需要独立的管理后台逻辑。

## 三、连接件：MCP + 配套知识打包

连接件目录结构（样本 `connectors/camscanner-mcp/`）：

```
camscanner-mcp/
├── mcp.json            # MCP 服务声明：远端 streamableHttp URL + 自定义头 + timeout
└── skills/
    ├── SKILL.md        # 使用知识：触发词堆叠的 description、能力边界、分流表
    └── references/     # 按场景的参数参考（image/pdf/office/云文档/工具组合/错误处理）
```

要点：

- **一个连接件 = 一个 MCP 端点 + 一份"怎么用它的知识"**。mcp.json 只声明传输（全部远端 http/streamableHttp + header 标识宿主 `X-IS-AGENT: workbuddy`），SKILL.md 承担触发匹配（description 前置海量触发词）与执行纪律（"以当前连接器可见工具及其 schema 为准，不因文档列出工具就认定可用"、"认证由连接器管理，不手动配置凭据"）。
- **市场目录字段**（`catalog/connectors.json`，443 条）：`id/name×2/description×3`（中英三份）、`examples_zh/en`（给路由用的触发例句）、`auth_mode`（token 102 / oauth 19 / server-side 17 / oneid-token 1）、`type`（**mcp 369 + cli 50**）、`visible_in`（internal / iOA / cloudhosted / selfhosted 四渠道各 ~150，另有 `plan:ultimate/exclusive` 付费墙）、`minWorkbuddyVersion`、`provider_id`。
- **同一目录按渠道可见性分发**（企业内部 / iOA 零信任 / 云托管 / 自托管），server-side auth 的连接件凭证托管在服务端，本地只拿 URL——本地不碰 OAuth 密钥。
- **启用状态分工作区**：`~/.workbuddy/connectors/<workspaceId>/connector-states.v3.json`；默认 `mcp.json` 预置腾讯系远端 MCP（qq-mail、ima、腾讯文档、腾讯问卷、TAPD…）全部 `disabled: true`，用户显式开启。
- 名字空间：MCP server key 一律 `connector:<id>` 前缀，与用户自配 MCP 区分。

## 四、skill 格式与生命周期

frontmatter 在 Anthropic 通用格式（name/description）上扩了运行时字段：

- `allowed-tools`：skill 绑定工具白名单（如 installer 只许 `search_and_install_skills`）
- `disable` / `disableModelInvocation`：用户开关 / 模型自动调用开关分离
- `agent_created`：标记 agent 生成的 skill，允许 `skill_manage` 工具后续改删
- `context` / `agent` / `model`：skill 声明自己属于哪个上下文/子代理/模型
- `userInvocable`、`overrideKey`、`slug`、`iconSource`：入口与覆盖语义

扫描产物（`.skill-list-cache.json` v6）每条带 `filePath/source(builtin|userSettings|plugin)/type(prompt)/disable/disableModelInvocation`。**skill 对模型是 `type: "prompt"`**——不是工具，是按匹配注入的作业知识。

## 五、运行时架构（codebuddy-lite）

1. **PTC / Code Mode**：REPL 是唯一直接工具；files/shell/search/web/media/MCP resources/子代理/tasks 全部是 REPL 沙箱内的可编程对象。系统提示明确："别用 Read/Glob/Grep 探测 skill 目录，skill 文档已在 REPL `code` 参数里"——**目录枚举被文档注入替代，上下文经济 + 反探测一条纪律同时解决**。无 Write 工具（Edit 空旧串或 heredoc 造文件）；Bash 返回结构化 `{stdout, stderr, exitCode, signal}`。
2. **MCP 工具代理**：`createToolProxy` 包每个 MCP 工具，带 `server_name`、`defer_loading`（默认 true，延迟加载）、`inputSchemaFingerprint`、`prompt_cache_stable` 标记——为提示词缓存做工具面稳定性管理。
3. **权限链**（MCP 工具调用序）：PreToolUse hook 结果 deny → `ToolHookManager.executePreToolUseHooks`（可 deny / **modifiedInput 改写入参**）→ `authorizeMcpEgress`（网络出口按 `mcp:<server>/<tool>` 授权）→ loop guard（`__loopGuardCallId` + `replSandboxDepth==0` 才检查）。
4. **计费闸门**（`hooks/high-credit-gate.cjs`，479 行，注释即文档）：
   - 插件在自己的目录放 `high-credit-tools.json` 清单即声明计费工具——**全链路单源、零硬编码**，daemon 的 hook matcher 与审批分组 env 都从同一份清单派生；
   - 命中即 `permissionDecision: 'ask'`，且判定发生在所有权限规则与 bypassPermissions **之前**——默认 bypass 模式也拦得住扣费工具；
   - **fail-closed**：任何异常兜底为 ask（hook 抛错会被 fall-through 成放行 = 静默扣费，故宁可多弹）；如实记录边界（单插件清单运行期损坏会放行该插件，防线在构建期校验）；
   - 接口纪律：stdout 必须纯净单行 JSON；诊断走 stderr 且**不得打印 tool_input（PII）**；非目标工具只能 `{"continue":true}`，绝不返回 allow；exit code 恒 0；
   - 审批元数据经 `hookSpecificOutput['codebuddy.ai/approval']` 透传到 ACP `toolCall._meta`，宿主浮层按 kind 渲染高积分文案。
5. **插件声明式配置**：`.mcp.json` 支持 `${CODEBUDDY_PLUGIN_ROOT}` / `${CODEBUDDY_PLUGIN_DATA}` 变量、`defer_loading: false` 显式声明（计费工具必须直连，否则被 DeferExecuteTool 短路绕过闸门）；`.codebuddy-plugin/plugin.json` 声明 `welcomeModes`（code/design/work）与 `runtimeOwner`。
6. **桌面形态**：`~/.workbuddy/` 有 audit-log（按天 jsonl）、sqlite 迁移链（session_plugin_context、workspace_trust_decisions、automation_runs、cloud_conversations…）、自带 node/PortableGit/python 工具链、企业微信 A2A 通道（`@tencent/wwapi-electron` + wechat-copydata-decoder）。

## 六、提示词架构

- **fragments 模板拼装**：`prompt-common/fragments/`（agent-to-agent、memory-context、wecom 路由 ×6、channel prompts、memory system）+ `interactionmode/{ask,craft,expert,plan,quick}/fragments`。nunjucks 式 `{{变量}}` + `{% if %}` 条件——提示词是带数据条件的模板系统，不是死文本。
- **三层记忆**（`workbuddy-memory-system.md`）：云记忆（服务端画像自动注入，本地只读 + 会话结束覆盖）+ `conversation_search`（服务端检索历史会话，明确"零权访问当前对话、query 必须自包含"）+ 本地记忆层。每层读写策略写死在提示词里。
- **通道路由**（`wecom-routing.md`）：企业微信操作收敛到两个通道（A2A 代理 Dayuan 管消息、`wecom-unified` skill 管非消息），明令禁直调 HTTP API，连术语都钉死（中文永远写"企业微信"不写"企微"）。

## 七、可借鉴思想

按证据强度排序：

1. **连接件 = 传输声明 + 使用知识打包**（`mcp.json` + `skills/` 同目录分发）。工具怎么调的知识跟着工具走，市场一件分发——比"MCP 市场"只发端点地址的形态完整一层。
2. **计费/高危门做在 hook + 声明式清单，单源派生**：插件自己声明、matcher 与文案从清单派生、判定先于 bypass、fail-closed、stdout JSON 契约、PII 不出 hook——这是"约束冻在机器门"的完整工程样板（对应 tepeu 的检查口/回答篇）。
3. **PTC/Code Mode 的上下文经济**：工具文档注入 REPL 参数而非平铺 tool schema；skill 列表超限降级为"仅名字"；明令禁止用探测工具列目录。tepeu 给模型看篇可对照。
4. **生态自举为 skill**：安装/推荐/创建/合规全用 skill + 少量专用工具（search_and_install_skills、skill_manage、search_plugins）实现，主程序零管理界面硬编码。
5. **目录多渠道分发 + server-side auth**：一份目录 `visible_in` 控渠道可见性；凭证托管服务端、本地只拿 URL——本地 agent 不碰第三方 OAuth。
6. **提示词 = 条件模板 + fragments**：按通道/模式/开关拼装，记忆分三层且读写策略入提示词。
7. **反教材同样有用**：SKILL.md description 里堆几百字触发词（camscanner）是关键词匹配时代的补丁层——与 Trae 同病，验证"匹配该走结构化路由而非散文堆叠"。

## 八、不可达 / 未做

- 主系统提示词的运行时最终拼装结果（nunjucks 模板变量值）本地不可见，需抓包/运行时 dump（本机 WorkBuddy 在跑，未做干扰性调试）。
- expert/playbook/automation（企微机器人）运行面只见到配置与迁移表，未深挖。
- 市场目录 85MB 全量包未入库（可按 CDN URL + etag 重下）。
