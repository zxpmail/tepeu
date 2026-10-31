# QingClaw Reference

轻流 QingClaw（wingent-x desktop v1.7.0）逆向档案。主对话/搭建引擎提示词在服务端（qingflow.com），本地不可达；本档案收录**客户端可见的全部提示词面与企业语料**。

- 深读与结论 → [ANALYSIS.md](ANALYSIS.md)
- 三方对照（runtime/思维链/上下文机制 × WorkBuddy × 千问）→ [COMPARISON.md](COMPARISON.md)
- `prompts/scenario-library.md` — 108 条场景库（todo/entry/analysis/builder × 七域），`{key,title,description,prompt}` 结构还原
- `prompts/single-app-prompts.md` — 18 条单应用搭建提示词（字段级语义范本）
- `prompts/prompt-assembly.md` — 提示词组装系统（xt 能力后缀 / St 域报表词表 / wt 示例数据 / Ct 门户条款）
- `prompts/openui-lang-system-prompt.raw.js.txt` — openui-lang（声明式 UI 语言）完整提示词，客户端拼装，~13KB
- `prompts/integrable-products-templates.txt` — 公众号图文排版规格模板
- `prompts/raw-cjk-template-strings.txt` — 中文模板串原始抽取（57 段）

来源：`C:\Users\zxpma\AppData\Local\Programs\QingClaw\resources\app.asar` → `.renderer/*.js`。抽取脚本思路见 ANALYSIS.md §八。内容版权归轻流，仅限内部研究，勿公开分发。

关联：[[reference_trae_prompts]]（同为薄壳+云端引擎拓扑，抓包方法在彼条）、[[workbuddy-skill]]（服务端配置缓存先于抓包的教训）。
