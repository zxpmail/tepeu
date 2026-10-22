# trae-reference — Trae 内置 skill 系统完整提取

**来源**：Trae CN（本机安装，`%LOCALAPPDATA%/Programs/Trae CN`，VS Code fork）
**提取日期**：2026-10-22
**用途**：内部研究其 skill 系统与提示词工程手法，供 tepeu 的 skill 体系设计参考。**勿公开分发**（内容版权归 ByteDance/Trae 所有）。

## 提取方法（两条路径，互相印证）

1. **客户端逆向**：agent 核心编译在 `modules/ai-agent/bin/agent-tool-host.exe`（90MB），内嵌 skill 注册表 JSON 与部分 skill 明文。UTF-8 感知字符串抽取脚本在 `%TEMP%/trae-extract-utf8.js`，全量字符串转储 `%TEMP%/trae-strings-utf8.txt`（22MB）。
2. **官方 CDN 下载**（更完整）：注册表内含每个 skill 的 `package.zip` 下发地址（`p11-market.byteimg.com`，带 sha256 与版本号）。按名字去重后共 **26 个 skill**，全部下载解压至 `packages/`。 注册表条目解析件：`%TEMP%/trae-skill-registry.json`（334 条含渠道变体）。

Trae 的 skill 分发机制：客户端内嵌注册表 → 按 channel（`solo_work::default` / `solo_work::gpt_54` / `prod_2026xxxx` 等）下载 zip → 解包到本地 `work/`、`code/`、`trae/` 等变体目录 → 按 frontmatter（name/description/user_invocable/disable_model_invocation）做 skill 匹配。

## 关键发现：文档质感的直接来源

**`doc-writing-guide`**（3 个文件，1074 行）就是"文档质感"的本体 skill：

- `SKILL.md`（276 行）：意图解释（目的/受众/语气/范围/约束五要素）→ 人格采纳（"be bold — witty critic, patient teacher…"）→ 文体识别表 → 参考风格校准（Lonely Planet / Stripe Engineering Blog / HBR / Apple Newsroom）→ **禁 AI 腔**：
  - 中英双语禁词表（"可操作/可执行/检查清单" / "actionable checklist"）
  - 禁翻译腔、禁空泛最高级（"world-class"）
  - 自造抽象名词每段最多一个（"飞轮/入口层/会话层"类）
  - **同一结论只说一次**（引言总结 ≈ 核心判断 ≈ 结论 三处复述 = 违规）
- `references/prd-document.md`（380 行）：PRD 子技能，按产品阶段/行业动态定深度，HTML 输出自动配行业配色
- `references/interact-prd-document.md`（418 行）：交互式 PRD

**格式路由链**：写报告默认路由到 `html-report` skill（自包含 HTML 交付物）；显式要 .docx/.pdf 才路由到 `docx`/`pdf` skill。即"文档质感好"背后是一整条 skill 流水线：`doc-writing-guide`（写作）+ `html-report`（排版）+ `html-deck`（演示）。

## 清单（26 个官方 skill 包）

<!-- INVENTORY -->
| Skill | 版本 | 文件数 | 描述（frontmatter 摘要）|
|---|---|---|---|
| TRAE-browseruse | 0.0.23 | 1 | (见 SKILL.md) |
| TRAE-browseruse-external | 0.0.19 | 1 | (见 SKILL.md) |
| TRAE-code-mode-orchestrator | 0.0.5 | 3 | (见 SKILL.md) |
| TRAE-computer-use-ptc | 0.0.33 | 1 | (见 SKILL.md) |
| TRAE-doctor | 0.0.4 | 2 | (见 SKILL.md) |
| digital-avatar-creator | 0.0.1 | 1 | "MANDATORY tool for creating digital avatar skills that run as autonomous SubAgents. MUST be invoked IMMEDIATELY when user wants to create a digital avatar, persona, virtual role, or specialized AI assistant." --- |
| dynamic-ui | 0.0.1 | 57 | (见 SKILL.md) |
| skill-creator | 0.0.1 | 1 | "MANDATORY tool for creating SKILLs - MUST be invoked IMMEDIATELY when user wants to create/add any skill" |
| TRAE-product-knowledge | 0.0.2 | 1 | (见 SKILL.md) |
| feedback | 0.0.1 | 1 | (见 SKILL.md) |
| schedule | 0.0.3 | 1 | (见 SKILL.md) |
| TRAE-plan-mode | 0.0.1 | 1 | (见 SKILL.md) |
| TRAE-spec-mode | 0.0.1 | 2 | (见 SKILL.md) |
| design-library-creator | 0.0.2 | 47 | (见 SKILL.md) |
| solo-design | 0.0.1 | 108 | (见 SKILL.md) |
| solo-graphic-generation | 0.0.1 | 4 | (见 SKILL.md) |
| solo-image-edit | 0.0.1 | 4 | Edit existing bitmap images with image-to-image generation and place every kept result on a `.design` canvas. Use for structured Design image comments or explicit pixel-level edits with an original image. --- |
| gp-agent-handoff | 0.0.2 | 1 | "Create a truthful, evidence-bounded handoff to the Master Agent. Invoke immediately before a General Purpose subagent returns its final result." --- |
| doc-writing-guide | 0.0.1 | 3 | (见 SKILL.md) |
| docx | 0.0.1 | 62 | (见 SKILL.md) |
| html-deck | 0.0.8 | 189 | (见 SKILL.md) |
| html-report | 0.0.6 | 119 | (见 SKILL.md) |
| pdf | 0.0.1 | 14 | (见 SKILL.md) |
| pptx | 0.0.1 | 85 | (见 SKILL.md) |
| research-guide | 0.0.4 | 3 | (见 SKILL.md) |
| xlsx | 0.0.1 | 53 | (见 SKILL.md) |


## 目录结构

```
memory/trae-reference/
  README.md                    本文件
  context-and-memory-notes.md  上下文工程与记忆系统分析（chronicle/ckg/ContextFS）
  builtin-mcp/dist/            SOLO 内置 MCP server 源码（chronicle 记忆系统等，709KB）
  packages/<skill>/            官方包解压件（保留包内原始目录层级）
```

## 上下文工程层（第二轮提取，2026-10-22）

- **chronicle**：SOLO 屏幕记忆 MCP。三设计可抄：返回强制包 `trust:'untrusted_observation'`（注入防御做在边界）；响应硬上限（12/20 条、512 字符 query、64KB 结果）；opaque ID 不透真实路径。详见 `context-and-memory-notes.md`。
- **ckg**（端口 50000）：本地代码知识图谱，Rust 服务 + 本地 embedding + sqlite_vec 向量存储——代码库 RAG 不走云。
- **ContextFS**（ctx-cli.exe）：`context://` 虚拟文件系统，L/H 优先级标记，IDE 的 Glob/Grep 挂在其上。
- 主对话系统提示词与补全提示词均在服务端，本地无。

大文件不在仓库：字符串转储与脚本均在 `%TEMP%`（可能随系统清理消失，可按上文方法重新生成）。
