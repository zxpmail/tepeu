# 三方对照：QingClaw × WorkBuddy × QianwenApp（runtime / 思维链 / 上下文机制）

> 2026-10-31。依据：千问 ANALYSIS（14.5MB 全扫穷尽）、WorkBuddy ANALYSIS §五/§六（连接件+运行时深读）、QingClaw ANALYSIS §三-§十（渲染层，服务端引擎不可达）。
> **证据基线不等**：QingClaw 各项排位证据强度偏低（薄壳，引擎不可达），已在表中标注。

## 一、Runtime

| | WorkBuddy | 千问 | QingClaw |
|---|---|---|---|
| 循环位置 | 本地（桌面进程内） | 本地（bundle） | 服务端（客户端只有代理） |
| 执行安全 | 进程沙箱（sandbox-center broker 逐操作裁决+决策记录 modifier 严格校验+Job Object 杀进程树） | 模型裁判，无进程级沙箱实证 | 不可考 |
| 权限机制 | hook 链（deny→modifiedInput→egress 授权→loop guard）+ 计费门先于 bypassPermissions、fail-closed | 授权来源白名单审查器（三轴分立/禁止组合/反靶校准） | 不可考 |
| 客户端运行时绝活 | tsbx 沙箱 | 收尾裁判 / PLAN-REACT 路由 | openui-lang 解析-求值-合并引擎 |

**产物信任的三种解法**：WorkBuddy 信沙箱（REPL 内执行）、千问信裁判（SendUserFile+审查器）、QingClaw 信解析器（流式容忍解析报告/闭集求值无 eval/按名合并+孤儿 GC/错误三元组带 hint 注回）。QingClaw 的解法与 tepeu 验收模型输出同构。

排名：执行安全 WorkBuddy >> 千问 > QingClaw（按可见面）；产物验收 QingClaw 第一。

## 二、思维链

| | 千问（管住） | WorkBuddy（绕开） | QingClaw（呈现） |
|---|---|---|---|
| 生成时 | "说'我会'=未执行，只能调工具"；调工具时禁夹带人话 | —（REPL code 参数即思考） | openui-lang 禁解释（fence 外才算聊天） |
| 收尾时 | 收尾承诺裁决器 | 子代理契约"完成状态禁走私" | xt 后缀"结论必带判断依据"（审计型暴露） |
| 展示时 | 思考翻译器 | 无 | 双流 SSE（thinking/generation delta）+ think/orchestration/intent_refinement 分相 + 业务阶段命名 + 失败阶段默认折叠 |

三种哲学：思维链=风险（千问）/ =介质（WorkBuddy）/ =UI（QingClaw）。纪律千问最全（双端拦截独一份）；QingClaw 的审计型暴露（推理不全程放开但结论必带依据/口径/排序依据）是给人看的过程的准入标准；WorkBuddy 用 PTC 消解问题，代价是推理不可见。

排名：纪律 千问 > QingClaw > WorkBuddy（未证实有）；呈现 QingClaw > 千问 > WorkBuddy。

## 三、上下文机制——三种战略，不是三个分数

| | 千问（治） | WorkBuddy（防） | QingClaw（协议+拉取） |
|---|---|---|---|
| 核心假设 | 长会话必胀，造压缩系统 | 别让它胀：文档随 REPL code 注入、defer_loading、prompt_cache_stable | 边界显式化：记忆工具化 pull、截断警告协议、注入打标清洗 |
| 深度实物 | 四级流水、摘要三道机审、增量压缩五操作、防跑、观测 schema 化 | stale-turn、禁探测列目录 | 五层记忆 query 工具、bootstrapMaxChars 预算、untrusted 标注、HITL 消息类型、find_tools_by_semantic |
| 代价 | 系统复杂（三道机审才敢用） | 长会话胀了没有 B 计划 | 模型可能忘记查记忆（pull 固有风险） |
| 记账 | 压缩策略省不出钱被记录失效 | 缓存命中稳定 | 会话级消耗排行 |

三种战略对应三种产品形态：千问=长任务桌面助理（必胀→治）、WorkBuddy=工作台+REPL（代码中转→防）、QingClaw=业务薄壳（按需查询→协议）。**没有抽象的最好，只有配会话形态**。tepeu 一问一答 ≤32 问无长会话：防+协议够用，治不需要——与给模型看篇"不包括压缩"裁定互证。

## 四、总表

| 维度 | 第一 | 第二 | 第三 |
|---|---|---|---|
| runtime 执行安全 | WorkBuddy | 千问 | QingClaw（不可考） |
| runtime 产物验收 | QingClaw | 千问 | WorkBuddy |
| 思维链纪律 | 千问 | QingClaw | WorkBuddy |
| 思维链呈现 | QingClaw | 千问 | WorkBuddy |
| 上下文深度 | 千问 | — | — |
| 上下文协议化 | QingClaw | WorkBuddy | 千问 |
| 上下文经济 | WorkBuddy | QingClaw | 千问 |

## 五、对 tepeu 的三条启示

1. **验收报告结构学 QingClaw**：解析结果分开报 incomplete/unresolved/errors，"部分合格"是一等状态；错误四元组 {source, code, message, hint}，机器码给程序、hint 给人、渲染层错误注回同一管道。
2. **过程呈现抄 QingClaw 三件**（给模型看篇将来件）：分相命名、失败默认折叠、审计型暴露。
3. **最便宜的两条改进**（QingClaw 独有）：截断显式化（超预算注入协议化警告，不静默）+ 注入内容打 untrusted 标、展示前清洗。

关联：[千问 ANALYSIS §九/§十二](../qianwen-reference/ANALYSIS.md)、[WorkBuddy ANALYSIS §五/§七](../workbuddy-reference/ANALYSIS.md)、本档案 ANALYSIS §八/§十。
