# 服务端提示词配置快照（acc-product-config-v3）

- 来源: ~/.workbuddy/cache/acc-product-config-v3.json（运行时从 https://www.workbuddy.cn 拉取的产品配置缓存）
- date: 2026-10-15T01:58:18.676Z · genieVersion: 5.8.3 · deploymentType: SaaS · platform: CLI
- 环境变量链: ["ACC_PRODUCT_CONFIG_V3","ACC_PRODUCT_CONFIG_V2","ACC_PRODUCT_CONFIG"] / 路径覆盖: ACC_PRODUCT_CONFIG_PATH
- acc-product-prompts.json: 128 篇命名提示词（identity/tool descriptions/system-reminders/output-styles/team/workflow/deep-research）
- acc-product-agents.json: 20 个内置 agent 花名册（含工具面与 instructions 引用）
- 主身份 = prompts["cli-agent-prompt"]（19831 字符，nunjucks 模板），开头 "You are CodeBuddy Code."
- 仅收录产品文本（prompts/agents 花名册）；原配置的 endpoint/enterpriseId/telemetry/qimei 等不入库
