# QianwenApp（千问 PC 端）提示词逆向档案

> 2026-10-28 提取。仅内部学习参考；提示词文本版权归阿里巴巴，勿外传勿直接复用。
> 沿用 WorkBuddy 档案纪律：只收产品提示词面，不收会话/审计/凭证数据。

## 标本

- 安装目录：`C:\Users\zxpma\AppData\Local\Programs\QianwenApp\`，版本 `5.0.7.276`（BUILD_VERSION）
- 形态：Chromium 系原生壳（非 Electron 单 asar），版本化目录 + `Resources/quantum_apps/` 三 asar
- 正主：**`cowork-agent-host.asar`**（agent 宿主，内含 14.5MB `engine/bundle.cjs`，全部提示词面在此）
- 附带：`qwen-voice-agent.asar`（语音，`fixed-prompt-manifest.js` 有版本化提示词政策）
- `qwen-light-apps.asar`：纯基础设施，无提示词面
- `~/.qwen/skills/`：社区技能（ui-ux-pro-max 等，本仓库 Skill 列表同款），**非产品原创，未收录**

## 提取方法

asar 无 npm 包依赖手工解析：头布局 `jsonLen=uint32LE@12`、JSON@16、`dataStart=8+uint32LE@4`（文件条目 `offset` 是**字符串**，须 `Number()` 转换——`dataStart + offset` 会字符串拼接）。提示词按反引号边界截取；被嵌套模板串截断的改用字节段整段落盘（`.js.txt` 后缀 = 压缩 JS 源内含提示词，非纯文本）。

## 收录物（prompts/）

| 文件 | 内容 |
|------|------|
| `main-system-sections.js.txt` | 主系统提示词全部段落源（41KB：intro/AGENTS/doing-tasks/文件交付/文件版本/工具/语气/输出效率/persona 全套） |
| `subagent-fleet.js.txt` | 子代理舰队 + 装配代码（54KB：general/exploration/planner/workflow/fork + ACTIONS_SECTION） |
| `memory-system-sections.js.txt` | 记忆系统段（与 Claude Code 逐字同构：四类型/不记清单/取用时机/TRUSTING_RECALL） |
| `plan-mode-and-compaction.js.txt` | 计划模式 + 上下文压缩提示词区 |
| `plan-scenario-library.js.txt` | 计划场景库（code/research 两份计划骨架模板 + 注入装配） |
| `permission-card-explainer.md` | 权限卡解释专家（向非技术用户解释命令后果） |
| `tool-action-reviewer.md` | **独立工具动作审查器**（18KB：授权来源白名单/信任分级/组合影响） |
| `turn-end-promise-judge.md` | **收尾承诺裁决器**（回复是否仍带未兑现动作承诺，true/false 契约） |
| `message-classifier.md` | 消息分类器（任务 vs 闲聊） |
| `skill-recommender.md` | 技能推荐（返回 JSON 数组下标） |
| `thinking-translator.md` | 内部推理独白中译展示 |
| `title-generator.md` | 会话标题生成（4-12 字） |
| `plan-vs-react-router.md` | PLAN/REACT 路由小模型 |
| `research-controller.md` | 研究充分性控制器（Python f-string 源） |
| `research-compressor.md` | 研究压缩简报 |
| `voice-prompt-manifest.js.txt` | 语音代理固定提示词清单（政策版本号 + BACKEND_ACCEPTED_RESPONSE_POLICY） |

## 未收录与边界

- `results.txt`（构建校验清单，含阿里内部员工邮箱）——存在即报，未拷贝
- 会话/聊天/遥测数据：未触碰
- `engine/bundle.cjs` 原件未拷贝（14.5MB，只取提示词区段）
- Antigravity/Claude Code 后端集成端点变量（`ANTIGRAVITY_ENDPOINT` 等）——只记存在
