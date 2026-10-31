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

## 九、复现方法

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
