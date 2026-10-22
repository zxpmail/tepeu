# Trae Skill 系统与提示词工程逆向分析

> 2026-10-21/22 三轮提取的综合报告。证据全部来自本机 Trae CN v3.4.4 安装目录与用户数据，无抓包。原始产物见同目录 `packages/`、`builtin-mcp/`、`builtin_skills/` 及两份专项笔记。

## 一、核心结论

Trae 的"文档质感好"由三层合力构成，每一层都有实物证据：

1. **专职写作 skill**：`doc-writing-guide`（276 行）把中文 AI 八股形式化为可执行的改写规则
2. **格式路由流水线**：写报告默认路由 `html-report` skill 产出自包含 HTML，显式要求才走 `docx`/`pdf`——质感好的一半来自排版层而非写作层
3. **子代理编排**：文档由专职子代理写，主代理禁止改写，写完只回报告文件不回读——杜绝自检循环

主对话系统提示词不在本地（日志无命中、对话库加密、workbench/exe 排除），在服务端按 `(agent 形态 × 模型 × skill 渠道)` 拼装，本地只存拼装参数。

## 二、提取路径（可复现）

| 轮次 | 路径 | 产出 |
|------|------|------|
| 1 | exe 内嵌注册表 JSON（name/sha256/url）→ ByteDance CDN 下载 `package.zip` | 26 个官方 skill 包，765 文件 |
| 2 | `byted-solo.builtin-mcp/dist` 拷贝 + `modules/ckg`、`ctx-cli.exe` 字符串分析 | chronicle 记忆系统源码、上下文层笔记 |
| 3 | `~/.trae-cn/builtin_skills/` 拷贝 + `state.vscdb` 读取 | 5 个物化 skill（含中文 system-prompt 实物）、32 模型路由表 |

关键机制：客户端内嵌 skill 注册表，按 channel（`产品线::模型渠道::构建批次::平台`）从 CDN 拉包，本地物化后按 frontmatter 做匹配。**skill 是随模型迭代的活文件，不是静态资产。**

## 三、架构发现

### 3.1 上下文工程三层

- **ckg**（端口 50000）：Rust 本地代码知识图谱，本地 embedding + sqlite_vec 向量存储，代码库 RAG 不走云
- **ContextFS**（ctx-cli.exe）：`context://` 虚拟文件系统，L/H 优先级标记，IDE 的 Glob/Grep 挂在其上——agent 看到的是上下文 VFS 而非原始磁盘
- **chronicle**：OCR 屏幕记忆（10 分钟/6 小时两级摘要），MCP 工具暴露

### 3.2 边界防御做在代码层

chronicle 三个设计：每个工具返回强制包 `trust: 'untrusted_observation'`；响应硬上限（recent ≤12 条、query ≤512 字符、结果 ≤64KB，超限抛错）；`memoryId` 只收 opaque ID 不透真实路径。注入防御不依赖提示词自觉。

### 3.3 模型路由（32 模型）

按 agent 形态（solo_agent / solo_coder / solo_work_lite…）分组配模型。默认 Seed-Code（字节自家），其余为 DeepSeek-V4、GLM-5.x、Kimi-K3、Qwen3.8、MiMo-V2.6、MiniMax-M3 等国产模型。每个模型条目约 50 字段，其中 `native_function_call`、`enable_invalid_json_hint`、`is_new_pe`、`open_optimize`（仅 Seed 系）、`parallel_tool_calling`（仅 Seed-2.1-Pro 显式 false）是按模型差异化的提示词/行为开关；`prompt_max_tokens` 是提示词总预算。

## 四、提示词工艺评价（分层打分）

| 层 | 得分 | 依据 |
|----|------|------|
| 文档写作（doc-writing-guide） | 9/10 | 反模式形式化为改写规则，每条配 ❌/✅ 实例对 |
| 工具描述（chronicle MCP） | 8/10 | 一句话讲清返回+信任边界，少即是多 |
| 编排（design-library 工作流） | 7/10 | 相位守卫/报告契约一流，但靠重复禁令打补丁 |
| 系统提示词（mini-app） | 7/10 | 约束具体（Taro 4.1.9、750rpx、`wx.`→`Taro.`），结构常规 |

doc-writing-guide 最有价值的三处：

- **句级反模式表**（§3.5）：虚词结构（"进行分析"→"分析"）、不可验证的"确保"（→换成可测量指标）、空洞强调词（→换成数据）
- **段落级反模式表**（§3.4）：heading-echo、meta-narration、宣言式开头、insight-escalation frames（"不是X而是Y"式伪深刻）——中文 AI 八股的系统性封杀，英文 prompt 库无对应物
- **子场景路由**（§6）：有序决策规则 + 6 条路由实例表，程序化而非凭感觉

体系性弱点：验证手段以散文门为主（如 security-review 的 "confidence floor = 0.8" 是模型自报数字，无下游校验），机器门稀缺（chronicle 的 64KB 上限是同体系内唯一的代码强制）；规则堆叠有明显的补丁层累积痕迹（"Do NOT count lines"、"zero tolerance" 式禁令重复出现）；同一份散文要伺候能力差异大的 32 个模型是其根因。

## 五、对 tepeu 的可落地借鉴

按优先级排序，每条注明来源：

1. **反模式改写规则搬进文档类 skill**（doc-writing-guide §3.4/3.5）：这是可直接复用的成品，无需改写
2. **信任标注做在边界**（chronicle）：tepeu 工具返回加 `trust` 字段 + 响应硬上限，注入防御进"检查口"而非提示词
3. **opaque ID 不透路径**（chronicle）：给模型的资源引用方式，"给模型看"篇可参照
4. **双向 description**（5 个 builtin_skills 全覆盖）："仅当…时使用。不得用于…" + debugger 式 Pre-Activation Intent Gate + bad/good few-shots——抗误触发设计直接照抄
5. **报告文件契约**（design-library）：子任务写完只回 `writtenFiles` JSON，禁止读回自检——上下文经济的核心机制
6. **skill 按模型/平台出渠道变体**（注册表 channel 机制）：skill 版本管理思路，避免单点维护伺候所有模型
7. **反面教材同样有价值**：mermaid 配色硬编码、"Do NOT count lines" 式散文微管理——验证"约束冻在机器门，不冻在提示词"原则的正确性

## 六、不可达部分

- 主对话/补全系统提示词：服务端下发，本地无明文
- 对话库（`ModularData/ai-agent/database.db`，4.6MB）：加密，随机字节头
- 抓包可行性低：Trae 网络层为 `sscronet.dll`（字节 TTNet/Cronet fork），可能绕系统代理且自带证书校验

## 七、产物索引

```
memory/trae-reference/
  ANALYSIS.md                     本报告
  README.md                       提取方法 + 26+5 skill 清单
  context-and-memory-notes.md     chronicle/ckg/ContextFS 专项分析
  local-data-notes.md             加密库结论 + 32 模型路由表 + 关联字段
  packages/                       26 个官方 skill 包（CDN 原样解压）
  builtin-mcp/dist/               SOLO 内置 MCP server 源码
  builtin_skills/                 5 个本机物化 skill（含中文 system-prompt）
```

临时产物（可重新生成）：`%TEMP%/trae-strings-utf8.txt`（exe 全量字符串）、`%TEMP%/trae-skill-registry.json`（334 条注册表条目）。
