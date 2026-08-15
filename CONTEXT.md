# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
已切到 **develop** 作为重写/开发线；**main** 冻在 v1.0（`c98fec8`，tag `v1.0.0`）。架构定界见 ADR-016。

## 上次停在哪
- ✅ `develop` 已创建并 push（`origin/develop`）；ADR-016 提交在 `fe7ab28`
- ✅ main 保持审计收口版，待重写成熟再合并

## 近期关键决定
- 严格内核：主体+命名空间、能力总线、会话事实日志；Loop 是运行时不是内核
- 企业可卖先薄（人/项目/审批/审计/预算/薄知识）；缝预留摊大饼
- Subagent / Teams / 长程 = 编排积木，Teams 用 preset，长程从 Schedule 演进
- Agent Flow（preset 图）vs Agentic Flow（工具循环）；可外层流程+内层自主；勿混一个 Orchestrator
- 每项目兜底 Agent：无专用 Agent 时普通对话/Skill 激活落此；禁无 Agent 走 Orchestrator
- dsh 插件不可直接加载；可 MCP/内容/外部引擎桥接；tepeu 自有 Adaptor 扩展面
- 向 dsh 续学：不变量、真压缩、超时/取消、TurnContext、配错即响、回放测、token 压力；开发可活、固化求稳准效率（不追热插）
- 模型路由：该有 ModelRouterAdaptor 缝；MVP 透传 providerId；不做自动难度选模；与图路由/总线分发分开
- 思维链三分开：model reasoning / agent plan / tool trace；事件化 + ReasoningPresenter；默认可展示不回灌
- 提示词：User=事件；System=PromptAssembly 分 Section 注册（技能/记忆/规则/工具 schema）；禁 Orchestrator 巨型拼接
