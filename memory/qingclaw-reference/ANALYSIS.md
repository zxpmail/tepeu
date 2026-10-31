# QingClaw 逆向档案（wingent-x）

逆向日期：2026-10-31。标本：`C:\Users\zxpma\AppData\Local\Programs\QingClaw`（Electron，v1.7.0）。

## 一、身份

- 包名 `@ai-fe/wingent-x-desktop`，appId `com.qingflow.wingentx`，productName `QingClaw`——**轻流（qingflow.com）的 AI 桌面端**，内部代号 wingent-x。
- 定位文案：「你的24小时业务助手」「通用智能体，可协助完成日常问答、资料整理、任务执行，并结合文件上下文开展协作」。
- `.openclaw/` 目录与 "Claw" 命名是 OpenClaw 血统痕迹，但本机 `~/.openclaw` 内容是旧实验残留（2026-05），与本 app 无关，勿混淆。

## 二、架构：薄壳 + 服务端引擎

- `app.asar` 24MB：`main.cjs`（express + http-proxy-middleware，本地 127.0.0.1:32123）+ `.renderer/`（React SPA，44 个 js）。
- 主进程只做四件事：窗口、`/api/` 与 SSE 反代到 qingflow.com、auth cookie 桥（token+wsId）、验证码 session 兼容钩子。
- **agent 循环与系统提示词在服务端**（qingflow.com wingent 后端），本地不经手——与 Trae SOLO 同款拓扑。本地缓存（HTTP cache / Local Storage leveldb）扫过，无提示词载荷。
- 连接器体系：企业微信（安装二维码流程）、钉钉、飞书——企业数据源。帮助文档 `help/connector-config.html` + 23 张截图内置。
- 桌面运行时另有 Python REPL / 文件读写 / PDF 导出 / 发布动作等工具面（见 bubble-fixtures）。

## 三、本地提示词面（全部入档 `prompts/`）

| 面 | 文件 | 内容 |
|---|---|---|
| 场景库 108 条 | `scenario-library.md` | 四能力（todo/data-entry/data-analysis/system-builder）× 七域（销售/采购/生产/项目/设备/财务/行政）|
| 单应用提示词 18 条 | `single-app-prompts.md` | 字段级企业语义最密集的展品 |
| 提示词组装系统 | `prompt-assembly.md` | xt/St/Ct/wt/Tt——场景正文 + 能力后缀 + 域词表注入 |
| openui-lang 提示词 | `openui-lang-system-prompt.raw.js.txt` | 客户端拼装的"AI 生成 UI 代码"契约，约 13KB |
| 图文排版模板 | `integrable-products-templates.txt` | 公众号排版规格（16px/1.75 行距/#268bfb 标题）|
| 定时任务提示词 27 段 | `scheduled-task-prompts.md` | 预设提醒（商机日报/续约/交付风险/维保到期/应收逾期…）+ cron 调度语义 + 执行智能体绑定 |
| 原始抽取 | `raw-cjk-template-strings.txt` | 57 段中文模板串原样 |

**注意**：场景库与组装逻辑是客户端 JS 明文（ prompt-as-config 的相反面：场景烧在客户端）；主对话系统提示词在服务端，本地不可达。内容版权归轻流，仅限内部研究，勿公开分发。

## 四、企业语义词表（从场景库语料统计）

**字段类型**（出现次数）：单选 130（必带完整枚举）、多行文本 99、文本 78、日期 70、成员 69、自动编号 57、数字 44、金额 37、附件 37、日期时间 31、部门 17、百分比 4、多选 2、引用字段（跨应用引用，必注明"引用「X」应用"）。

**视图类型**：表格视图（65）、看板视图（14，按状态分组）、日历视图（10，按日期字段）；系统级再加统计报表、数据仪表盘、门户。

**按钮动词法**：新增X、导入X、更新X、登记X、导出X、提交审批、分派、转商机、标记赢单/输单/无效、签到、完成、升级处理、申请销假/维修/归还/延期——动词+宾语构词，动词域高度收敛。

**流程规则六型**：
1. 状态机：新线索进入待分配；分配销售后状态变为待跟进
2. 条件必填：标记输单时必须填写输单原因
3. 通知：标记赢单后通知销售主管
4. 提醒：计划回款日期前提醒负责人
5. 审批路由：提交后流转给直属上级审批；折扣率超过权限阈值时进入审批
6. 校验/联动：提交后先校验金额和附件；新增跟进后更新关联客户或商机的最近跟进日期

**实体关系图**（引用字段统计）：枢纽实体 = 供应商档案(14)、客户档案(12)、项目立项(8)、设备台账(8)；次级 = 采购申请(5)、商机管理(5)、预算科目(3)、合同台账(3)；叶子 = 发票登记、车辆台账、办公用品库存、假期额度、会议室资源。每个域应用都引用枢纽，枢纽间基本不互引——星型模型。

**单应用提示词五段式**：字段结构（名：类型，必填；枚举全列）→ 布局要求（分N区、每区一行几字段、谁独占一行、移动端单列）→ 视图设计（全部X/我的X/看板/日历 + 各自按钮）→ 流程规则（2-3 条六型规则）→ 示例数据 + 设计文档。「全部X + 我的X（筛选负责人=当前用户）」是视图设计的固定起手式。

## 五、openui-lang：AI 生成 UI 的协议设计（对 tepeu 最有参照价值）

客户端 `chat-bubble` bundle 内嵌完整提示词拼装库，教模型输出**声明式 UI 语言**而非直接 HTML/React：

- **身份句**一句话 + 分节正文（Syntax Rules / Built-in Functions / Query / Mutation / Action / Interactive Filters & Forms / Edit Mode / Hoisting & Streaming / Inline Mode / Data Workflow / Important Rules & Final Verification / Available Tools）。
- **位置参数制**：`Stack([children], "row", "l")`，明说 "colon syntax is NOT supported and silently breaks"——解析器换来的模型负担，用反例讲。
- **静默失败三连警告**：未引用变量静默不渲染、位置参数错序静默断裂、@Each 循环变量只在模板内可用——同一约束在 Syntax Rules、Builtins、Example 三处重复（对关键约束冗余表述）。
- **Query/Mutation 二分**：读 = `Query("tool", args, defaults, refreshInterval?)` 带默认值先渲染再换真数据（流式友好）；写 = `Mutation` 只准由按钮 `Action([@Run(...)])` 触发。
- **编辑模式 = 按语句名 merge**：只输出变更/新增语句，删 = 从父 children 里摘掉（孤儿自动 GC）；给出 patch 尺寸表（改标题 1 句、加过滤 3-5 句），"超过 10 句请重新考虑"。
- **流式感知的语句排序**：root 先行（壳先出现）→ $变量 → Query → 组件定义 → 叶子数据；靠 hoisting（引用先于定义）实现渐进渲染。
- **Inline Mode 路由**：问题 → 纯文本不许带码；改 UI → 只出增量 patch 码块，解析器从 fence 里抽码，fence 外文本当聊天显示。
- **Data Workflow 反模式对照**：WRONG（调完工具把结果硬编码成 item1/item2/item3）vs RIGHT（Query + @Each + @builtins 派生），并给"何时允许硬编码"的豁免（纯静态展示数据）。
- **Final Verification 自检单**：可编号核查的 3-5 条（root 第一行、每个名字有定义且可达、每个 Query 结果被引用、每个 $binding 出现在组件里、每个可见过滤器 $binding 进了 Query args）。
- **工具文档自动生成**：从 JSON Schema（inputSchema/outputSchema）渲染函数签名 + 类型（CR() 递归类型渲染），工具描述不手写。
- 校验失败时错误消息带修复 hint：`The entire response must be valid openui-lang code starting with root = Root(...)`——错误消息即提示词。

运行时组件词汇：Stack/Col/Card/CardHeader/Tabs/Table/BarChart/LineChart/PieChart/ChartTooltip/ChartLegend/Form/FormControl/Input/Select/SelectItem/Button（shadcn 系）。内置函数 @Count/@Sum/@Avg/@Min/@Max/@Round/@Filter/@Sort/@Each，强制"计算值不许硬编码，用 @builtins 在 Query 结果上现算"。

## 六、对话样本夹具（bubble-fixtures）

营销场景演示对话（公众号排期），揭示工具气泡分类学：text / tool args+result（文件读写、list_files、代码执行 `score_topics(...)`、画图 `plt.savefig`、PDF 导出、发布动作 draft:true）/ 错误气泡（FileNotFound、PublishError、AttributeError 带中文消息）。**错误也是一等公民气泡**——渲染层为失败路径专门设计了展示。

## 七、对 tepeu 的映射

1. **两段式 + 词表注入**可直接用于回答篇/做事篇：场景正文与通用验收要求分离，抽象要求（"设计报表"）必须带域词表实例化。
2. **输出物清单化**收口（"请输出 X、Y、Z"）≈ 检查口的"先给切口清单"，QingClaw 把它做进了每条场景提示词的最后一个句子。
3. openui-lang 的 **Final Verification 编号自检** 与 **patch 尺寸表** 是"最小改动/验证"规则的产品化表述，可对照检查口篇。
4. **错误消息即提示词**（校验失败 hint 指向正确格式首行）——机器门失败输出应教人怎么改，不只是说不行。
5. 单应用五段式是"表单/应用生成"任务的**字段级语义模板**范本；星型实体关系 + 枢纽引用是轻流企业语义的骨架。
6. 架构上再次验证：toC 桌面 agent = 薄壳 + 云端引擎，提示词面分两层——**场景库烧客户端**（随版本发），**主系统提示词留服务端**（可热更）。搜提示词先扫渲染层 bundle，别急着抓包。

## 八、路由分工与质量评价（讨论结论，2026-10-31）

**路由分工**：人路由场景（卡片按能力×域分类树，点了灌输入框，零相似度匹配）→ 代码路由后缀（templateType 查 xt，编译期写死）→ 模型路由对话（自由输入走兜底提示词；对话内 code/text 二分与工具选择由模型判）。没有分类器。卡片分类树 = 引导漏斗（todo 轻→analysis 中→builder 重），description 全是名词短语堆叠。

**质量分层**：openui-lang A 级（每个警告对应真实解析失败，反例教学/尺寸预算/豁免条款都是踩坑产物）；组装架构 A-（词表注入聪明）；场景库正文 B+（质量来自词表不来自话术）。场景库具体瑕疵：模糊条件（"筛选预计金额较高"无阈值）、类型/状态重叠（商机阶段 vs 商机状态都含赢单输单；客户类型混生命周期）、分析类缺幻觉护栏与输出格式规格、零 few-shot。**学习姿势：抄结构不抄话术。**

**可学手法清单**：① 定时任务三句式（检查 X 找出 Y → 优先关注 Z（风险前置维度）→ 输出清单+影响+建议动作），12 预设提醒 + cron 语义 + 执行智能体绑定；② 错误消息教修复路径（"绑定的智能体已删除，请编辑任务并重新选择"）；③ 模板即规格（排版规则编码成带占位图 HTML 骨架，约束做成实物不写成话）；④ 夹具驱动 UI（消息契约用 fixtures 固化）；⑤ 工程壳层：cookie 桥登录复用 / requestStrategy proxy|direct / runtime-targets 构建期生成+环境覆盖 / OSS 更新；⑥ 表达式语言收敛（九个 @ 组合子出全部 KPI——语言越小越难错越好验）。

**openui-lang 运行时**（客户端内嵌解析/求值/合并引擎，chat-bubble bundle）：① 流式容忍解析——每个 chunk 全量重解析，解析结果是类型化报告 `{root, meta:{incomplete, unresolved[], orphaned[], statementCount, errors[]}, stateDeclarations, queryStatements, mutationStatements}`，半截代码也能部分渲染；② 错误三元组 `{source, code, message, hint}`（如 parser/parse-failed）+ 多源合并（解析器错误 + schema 校验错误 + 渲染层 `__openui_errors` 注回）+ stringify 去重；③ 编辑 = 按语句名 merge（replace/append）+ 孤儿 GC；④ 闭集求值——内置函数是注册表里的 JS fn（bR() 白名单检查，无 eval），Query/Mutation 语句静态提取成独立注册表（运行时先验知道哪些语句是读/写）；⑤ Query 默认数据先渲染后换真数据、$binding 变更自动重取、refreshInterval；⑥ Action 步序失败语义按操作类型分（mutation 失败截断后续步骤，query 重取 fire-and-forget 不可能失败）。**SSE 代理**（main.cjs）：上游强制 `Accept-Encoding: identity`（防 gzip 缓冲）、响应删 content-length + `no-cache, no-transform` + `x-accel-buffering: no`、SSE 双重识别（路径 /events/stream 或 Accept 头）、hop-by-hop 剥离、代理错误 502 纯文本——流式代理的坑全套处理过。

**思维链处理**（渲染层实证）：思考与正文是**两条独立 SSE 事件流**（`agent.thinking.delta` / `agent.generation.delta`），客户端按 segment kind 分装——thinking 可折叠、按阶段累计、带计数（"N 次思考"），思考原文在服务端。思考分相：`think`（推理）/ `orchestration`（编排叙述）/ `intent_refinement`（意图澄清）三种命名段型。fixtures 把一轮生命周期定为「思考 → 工具调用 → 工具完成 → 正文生成」，业务阶段叙述用业务动词命名（读取→整理→生成），**失败阶段默认折叠**。xt 后缀另有一层"审计型推理暴露"：结论必带判断依据/口径，但推理不全程放开；代码生成场景反向禁止解释（fence 外才算聊天）。

## 十、上下文机制（index bundle 实证，2026-10-31）

**内核工具注册表**（原文 `prompts/kernel-tool-registry.raw.js.txt`）分五类：meta（Bootstrap truncation warning、capability_dispatcher）/ file（read/write/delete/directory_list/search/grep/patch/download_url）/ **memory（query_long_term、query_short_term、query_dialogue_digest、read_archive、compress_hot、read_workspace_transcript）**/ runtime（python_interpreter、shell_executor）/ skill（manager_manual/source_authoring/list/get + authoring_guide）+ 工具发现组（list_toolsets、list_tools、list_tool_tags、**find_tools_by_tags、find_tools_by_semantic**、registry_refresh）。

**五层记忆，pull 型工具化**：热记忆（可压缩）→ 短期 → 对话摘要 → 归档 → 工作区记录 + 长期记忆。模型只通过 query 工具拉取，不自动注入；压缩（compress_hot）本身也是工具。与千问档案 §十二（四级流水、摘要三道机审——服务端自动管线）是两条路线：QingClaw 记忆=工具（模型自主拉取+压缩），千问记忆=流水（服务端分层摘要）。

**Bootstrap 字符预算 + 截断警告协议**：注入区有 bootstrapMaxChars / bootstrapTotalMaxChars 预算；截断发生时以 `[Bootstrap truncation warning]` 标题 + `- ` 条目（含 raw -> / injected / 预算值）注入消息流，客户端解析成结构化警告并从正文剥离——截断不静默，是协议。

**不可信注入标注/清洗协议**：注入内容带 `System (untrusted): [tag]` / `Sender (untrusted metadata):` 前缀 + `[Mon YYYY-MM-DD]` 时间戳 + `{...}` 元数据块；cron 注入带 `[cron:...]` 前缀 + `Current time:` 尾注。客户端展示前剥离。**注入时打标、展示时清洗**。

**HITL 是消息类型**：text / error / assistant_pending / sse_in_progress / **hitl_waiting / hitl_answered** / plain——人审往返是流里的消息类型（与 tepeu 点头/60 秒超时同型，但 QingClaw 把等待态显式建模）。

**工具目录上下文经济学**：全量工具不预载上下文，模型用发现元工具按需取（tags / **semantic 搜索** / registry_refresh）——Trae get_skill_detail 同款思路，铺到工具层。

**会话生命周期**：自动重命名带重试表 [0,180,420]ms；会话失效检测（"请重新发送一条消息开启新会话"）；运行中任务阻塞删除；**会话级消耗分析**（会话排行、Top 会话消耗分布）。

## 十一、复现方法

```bash
# asar 手写解析（npm 装不上 @electron/asar 时的路子）
node $TMP/qingclaw-asar-list.js  "<install>/resources/app.asar"          # 列文件
node $TMP/qingclaw-asar-extract.js "<install>/resources/app.asar" ".renderer" "$TMP/qingclaw/app"
# 提示词抽取
node $TMP/qingclaw-strings.js <bundle.js>        # 模板字符串扫描
node $TMP/qingclaw-extract-scenarios.js qingclaw-foxbHnSS.js out.md  # 场景库结构化
node $TMP/qingclaw-vocab.js <corpus.txt>         # 词表统计
```

脚本都在会话 %TEMP%（qingclaw-*.js），丢了可按本文件思路重写。userData `%APPDATA%/@ai-fe/wingent-x-desktop/`（运行中勿动，只读扫描过，无提示词）。
