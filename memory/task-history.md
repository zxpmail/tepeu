# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-10-02 | runtime | 按规格实现 tepeu/runtime：账、回答、检查口、做事、给模型看、判断、一轮和外面一道门 | 测试 48 条通过，0 失败。快捷方式两条因无管理员权限未跑。停止默认「停」和 stop，同意 60 秒 |
| 2026-10-03 | cli | Spring Boot 命令行，没有网页，真跑一轮 | 三个字是要做什么、限制、怎样算做完。问模型成功记成没做完。还没提交 |
| 2026-10-27 | memory/ | WorkBuddy（腾讯 genie/codebuddy-lite）连接件/skill/提示词面逆向收口：全量档案 + 吸收裁决 | 提示词面=服务端下发已实证（acc-product-config 128 篇快照入库 prompts/，主身份 "You are CodeBuddy Code."）；吸收裁决落 ANALYSIS.md §七：计费门五规矩→检查口/账、stale-turn+装配观→给模型看、反走私→判断，其余条件件/不吸收 |
| 2026-10-27 | specs+cli | 吸收裁决落地第一刀：检查口 G-01（说明读不出来的能力启动时挡下，其余照常） | 计费门五规矩对照后四条 tepeu 已有，唯一缺口是说明损坏未规定。CliRunner.jobs 改逐项装配坏者跳过并留一句话，exit 1 语义改为「没问成」；点头/范围矩阵声明化记为待裁开口。全树 67 绿 |
| 2026-10-28 | docs | 密钥认出裁定草案（handoff 待审区）+ 开口记入 rewrite-1 §8 | 草案口径：位置优先、内容不扫（WorkBuddy 同款），密钥位置表命中=范围不在，复用 GATE_BLOCKED；待人审三问在 handoff。Team/Delegate 编排契约深读入 workbuddy-reference 笔记 §六 |
| 2026-10-28 | memory/ | QianwenApp（千问 PC 5.0.7.276）提示词逆向收口：cowork-agent-host.asar 手工解析，17 件提取物入库 qianwen-reference/ | 血统=Claude Code 内核+中文消费 persona 层；吸收裁决三件：收尾承诺裁决器（完成权机器裁判）、授权来源白名单（点头枚举化）、澄清三条件+止损；详判在 ANALYSIS.md §八 |
| 2026-10-28 | specs+docs | 千问吸收项落地成文：判断.md 不包括补「正文里答应下一步做什么」；rewrite-1 §8 记压缩防跑两守则与机器裁判将来件 | 对照后三件不落：授权白名单、Prompt 不是授权（tepeu 已覆盖）、澄清三条件（无问人循环）。对照理由在 handoff 参考档案行 |
| 2026-10-31 | memory/ | QingClaw（轻流 wingent-x 桌面端 1.7.0）逆向收口：asar 手写解析，本地提示词面全量入库 qingclaw-reference/ | 架构=薄壳+服务端引擎（场景库烧客户端、主系统提示词留服务端）；108 条场景库（四能力×七域）+ 组装系统（场景正文+能力后缀+域词表注入+编号跨函数续接）+ openui-lang 完整契约；企业语义词表与对 tepeu 映射在 ANALYSIS.md §四/§七 |
| 2026-10-31 | memory/ | 三方对照（runtime/思维链/上下文机制 × WorkBuddy × 千问 × QingClaw）入档 COMPARISON.md | 三种产物信任解法（沙箱/裁判/解析器）、思维链三哲学（风险/介质/UI）、上下文三战略（治/防/协议）；tepeu ≤32 问形态下防+协议够用，与「不包括压缩」裁定互证 |
| 2026-10-31 | memory/ | Trae doc-writing-guide 写作 skill 质量评价（会话内评审，未入档） | 负空间规则=四标本唯一 LLM 写作病灶清单（one-statement/禁"不是X而是Y"/自造名词每段限一）；字数度量自相矛盾；抄禁令表不抄名词堆砌式风格校准 |
| 2026-11-01 | memory/ | Cursor 3.27.22 逆向收口：VS Code fork（workbench.glass.main.js 49.7MB）入库 cursor-reference/ | 主提示词在服务端（SystemPromptSpec）；cursorsandbox.exe Rust 沙箱（决策日志带 matched_rule，win 网络仅代理级自认）；hooks 20 事件=Claude Code 契约兼容；内置 skill 25 份全量入库=四标本最高质量语料（autopilot/goal 完成审计/loop）；对 tepeu 映射 §八 |
| 2026-11-01 | memory/ | Cursor 二三轮深挖+13 份按模型系统提示词捞回：state.vscdb cursorDiskKV 13 族全文（GPT 系 20-24K）、chats/store.db CLI 两版 | 压缩管线=投机双阈值+确定性降级+摘要禁自指+截图幸存；CoT 三道闸（长思考提醒实验/推理循环检测/空响应分类重试）；141 个客户端 prompt section，isFable5 逐字复用 Claude Code 文本；Grind 多 agent（task spec 向下/handoff 向上/代码走 git）；影子工作区 GetLintsForChange=编辑前自检 |
| 2026-11-01 | memory/ | 五方对照入档 cursor-reference/COMPARISON.md（Cursor×WorkBuddy×Trae×千问×QingClaw 六轴） | 执行安全 WorkBuddy>Cursor>千问；完成权 千问裁判器第一、Cursor 审计+影子自检第二；上下文深度 千问流水第一、Cursor 投机+降级第二；CoT 运行时管理 Cursor 三道闸第一；提示词语料 Cursor 141 section 最富；CC 已成事实标准（三厂商三种姿势接入） |
