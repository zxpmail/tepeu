# 五方对照：Cursor × WorkBuddy × Trae × 千问 × QingClaw

> 2026-11-01。前置：[三方对照（QingClaw×WorkBuddy×千问）](../qingclaw-reference/COMPARISON.md)（runtime/CoT/上下文三轴），本篇把 Cursor（§八-§十一）与 Trae（ANALYSIS 全篇）并入，维度扩到六轴。
> **证据基线不等**：千问提示词面穷尽（14.5MB 全扫）、Cursor 提示词面近全量（13 族 IDE + CLI 积木库）、WorkBuddy 128 篇服务端提示词本地缓存到手；Trae 主对话提示词未到手（路由表与 skill 面到手）；QingClaw 主提示词不可达（薄壳）。排名按可见面。

## 一、身份与拓扑

| | Cursor | WorkBuddy | Trae | 千问 | QingClaw |
|---|---|---|---|---|---|
| 厂商 | Anysphere | 腾讯 genie | 字节 | 阿里 | 轻流 |
| 形态 | VS Code fork IDE + CLI | 桌面工作台 | VS Code fork IDE | 桌面助理 | 业务薄壳桌面端 |
| 血统 | VS Code fork；Claude Code 格式四线兼容；Claude 模型直接复用 CC 提示词文本 | CodeBuddy 血统（codebuddy-lite） | 字节 byted-solo | **Claude Code 内核**（字面量实锤） | OpenClaw 痕迹 + 云端 wingent |
| agent 循环 | 本地（glass + agent-exec） | 本地（桌面进程内） | 本地（SOLO 变体走云 K8s） | 本地（bundle） | **服务端**（客户端=代理） |
| 提示词面 | IDE：服务端拼装下发（state.vscdb 缓存可捞）；CLI：**客户端 141 section 积木拼装** | 服务端下发（acc-product-config 128 篇，本地缓存） | 服务端按 `(形态×模型×skill渠道)` 拼装，本地只存参数 | **编译期内置** bundle.cjs | 主提示词服务端；场景库烧客户端 |
| 提取产出 | 13 族全文 + 2 版 CLI + 5 协议原文 + 25 skill | 128 篇全文 + fragments | 26 skill 包 + 路由表（60 模型×4 形态） | 全量提示词面（穷尽声明） | 场景库/组装系统/openui-lang |

**信号一（市场结构）**：三家把主提示词放服务端（Cursor-IDE/Trae/QingClaw），两家本地可全文提取（千问编译内置、WorkBuddy 缓存明文）。**放服务端防不住本地缓存**——五家全部可提取，只是成本差。Cursor 最讽刺：保密条款写进提示词，全文在 state.vscdb 里。

**信号二（收敛）**：千问=CC 内核实锤，Cursor=CC 文本+格式兼容，WorkBuddy hooks/permission 契约同构——**Claude Code 已是事实标准**，三个厂商三种姿势接上（内核 / 文本 / 契约）。

## 二、执行安全

| | 机制 | 强度 |
|---|---|---|
| WorkBuddy | tsbx+sandbox-center 逐操作裁决 broker、决策记录 modifier 严格校验、Job Object 杀进程树、deny 凭证目录静态规则、fail-closed 计费门先于 bypass | **第一**（唯一进程级全闭环） |
| Cursor | cursorsandbox.exe Rust helper、声明式 policy、decision.log 到 matched_rule、hooks 20 事件、MCP 指纹；Windows 网络层仅代理环境变量（自认无内核出口阻断） | 第二 |
| 千问 | tool-action-reviewer 模型裁判（三轴分立/禁止组合/反靶校准）+ 沙箱策略；无进程级沙箱实证 | 第三（规则最密但缺机器 enforcement 实证） |
| Trae | chronicle 边界防御在代码层（untrusted 打标/响应硬上限/oid 不透路径）；无进程沙箱；SOLO 走云端沙箱 | 第四 |
| QingClaw | 不可考 | — |

对照三方结论不变：**WorkBuddy 信沙箱、千问信裁判、Cursor 两者都要但 Windows 网络层自认短板、QingClaw 信不到**。Cursor 的独特贡献是 **decision.log 带 matched_rule_hash**——沙箱裁决可审计可回放，五家独一份。

## 三、完成权 / 判断

| | 机制 | 形态 |
|---|---|---|
| 千问 | **收尾承诺裁判器**（独立 1.4KB 模型裁判：无结构化工具调用才触发/不认固定措辞/豁免清单精确到"只豁免对应动作"/缺省 false） | 模型裁判，最先进 |
| Cursor | autopilot 完成权分离（永不自行 merge/转 ready，只报 ready）+ goal skill 完成审计（"treat completion as unproven"，逐条需求找证据分类 proves/contradicts/incomplete）+ shadow workspace 候选编辑先自检 | 审计协议+机器检查口 |
| WorkBuddy | 提示词纪律（"Verification means proving"、完成状态禁走私 `r.status ?? "done"`） | 散文门，措辞可抄 |
| QingClaw | openui-lang Final Verification 编号自检单 + 错误四元组 hint 注回（产物验收非完成权） | 产物面 |
| Trae | 散文门为主（confidence floor=0.8 是模型自报数字，无下游校验） | 最弱 |

## 四、上下文机制——五种战略

| | 战略 | 深度实物 | 代价 |
|---|---|---|---|
| 千问（治） | 长会话必胀，造流水 | 四级流水 snip→microcompact→autocompact→contextCollapse；**先算 reducible 再决定调不调模型**；增量压缩五操作；**摘要三道机审**（必含章节/标识符保全/诉求重叠）；压缩观测 schema 化 | 系统复杂 |
| Cursor（投机） | 压缩当分支预测 | 双阈值后台压缩（start/persist 分离）；全败走**确定性降级**（不依赖模型）；**最新截图幸存**；preCompact hook；请求组装恢复遥测分类 | 依赖服务端配置调优 |
| WorkBuddy（防） | 别让它胀 | 文档随 REPL code 注入、defer_loading、prompt_cache_stable、stale-turn 作废声明 | 胀了没 B 计划 |
| QingClaw（协议+拉取） | 边界显式化 | 五层记忆 query 工具化 pull、截断警告协议、untrusted 打标清洗、find_tools_by_semantic | 模型可能忘记拉 |
| Trae（基建） | 上下文=文件系统 | ContextFS VFS（L/H 优先级）+ ckg 本地知识图谱（Rust，RAG 不出云）+ chronicle OCR 屏幕记忆 | 基建重，缺压缩面 |

**Cursor 补两味三方没有的药**：①**压缩质量有确定性兜底**（千问三道机审管质量，Cursor 确定性降级管可用性——两个都该学）；②**规则四挂载**（alwaysApply/globs/agentRequested/global）——上下文相关性挂载的正解，五家独一份。

## 五、思维链

| | 哲学 | 机制 |
|---|---|---|
| Cursor | CoT=可观测的故障面 | **三道闸**：长思考超阈值延迟注入提醒（A/B 实验）；推理流循环检测（重复行指纹→注入警告→整消息重试）；空响应分类重试（retry_thinking_only 独立类型）+ no_thinking_in_code（禁拿注释当草稿）+ redacted_thinking 透传 |
| 千问 | CoT=风险，双端拦截 | 生成时（"我会"=未执行只准调工具）+ 收尾时（承诺裁判器）+ 展示时（思考翻译器） |
| QingClaw | CoT=UI | 双流 SSE 分相命名、失败默认折叠、审计型暴露（结论必带依据） |
| WorkBuddy | CoT=介质 | PTC 消解问题（REPL code 即思考），代价推理不可见 |
| Trae | CoT=路由参数 | 路由表按模型配 thinking 开关；展示面与纪律面证据弱 |

**Cursor 是唯一把 CoT 当运行时故障管理（而非纪律或 UI）的**：超长、打转、空转是三类机器可检事件，各有闸+遥测。

## 六、扩展生态

| | 生态 | 格式 |
|---|---|---|
| Cursor | 25 内置 skill + MCP + rules 四挂载 + hooks 20 事件 + subagent | **Claude Code 格式全兼容**（不发明格式） |
| WorkBuddy | skill/connector/expert 三生态全市场分发，生态自举为 skill（管理后台零硬编码） | Anthropic SKILL.md 扩展字段 |
| Trae | 26 官方包 CDN 渠道分发，**skill 是随模型迭代的活文件**（60 模型×4 形态路由） | SKILL.md + 渠道 tag |
| 千问 | skills 渐进披露 + 子代理舰队 5 型（planner 严格只读/fork-branch 克隆打岔） | CC 同构 |
| QingClaw | 场景库 108 + 定时任务 27——业务场景库，非 skill 形态 | 自有 openui-lang |

## 七、总表

| 维度 | 第一 | 第二 | 第三 |
|---|---|---|---|
| 执行安全 | WorkBuddy | Cursor | 千问 |
| 沙箱可审计性 | Cursor（decision.log） | WorkBuddy | — |
| 完成权 | 千问（裁判器） | Cursor（审计+影子自检） | WorkBuddy |
| 上下文深度 | 千问（流水+机审） | Cursor（投机+降级） | Trae（基建） |
| 上下文经济 | WorkBuddy | QingClaw | 千问 |
| 上下文挂载协议 | Cursor（规则四挂载） | QingClaw | WorkBuddy |
| CoT 运行时管理 | Cursor（三道闸） | 千问（双端拦截） | QingClaw |
| CoT 呈现 | QingClaw | 千问 | Cursor |
| 产物验收 | QingClaw | Cursor（shadow lint） | 千问 |
| 提示词工程语料 | Cursor（141 section + 13 族） | 千问（穷尽全量） | WorkBuddy |
| 扩展生态成熟度 | WorkBuddy（三生态自举） | Trae（活文件路由） | Cursor |

## 八、对 tepeu 的增量启示（不与各档案 §八 重复）

1. **检查口三件套升级**（Cursor 补千问 WorkBuddy 没有的）：影子验证环（候选编辑先过 lint 再落盘）> decision.log 裁决审计（每次放行带 matched_rule 可回放）> 压缩/摘要也要机审（千问三道机审 + Cursor 确定性降级——**基础设施本身过检查口**）。
2. **格式战裁定**：Cursor 全接 CC 格式零成本换生态；千问直接换 CC 内核；WorkBuddy 契约同构但生态自建。tepeu "不发明格式"已写在 project-memory，五家对照进一步坐实——**跟随者红利在提示词工程上同样成立**（Cursor 直接复用 CC 文本）。
3. **CoT 管理按故障分类写**：超长/打转/空转三道闸 + 每闸带遥测——判断篇将来写 CoT 时照这个分类，不写"要好好思考"式散文。
4. **压缩将来件的设计顺序**（千问 §十二 + Cursor §十一 合并）：管道次序（免费→贵）→ 路由先算 reducible → 质量：三道机审（千问）→ 可用性：确定性降级（Cursor）→ 多模态保视觉状态（Cursor）→ 摘要禁自指（两家同）。
5. **多 agent 通信一句话**（Grind）：task spec 向下、handoff 向上、代码走 git——tepeu 将来长出多 agent 时这是现成的第一段文档。
