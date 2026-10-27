---
name: "guiputi-report"
display_name: "工作汇报"
display_name_en: "Work Reports"
description: "周报、月报、工作总结、工作计划：先问清给谁看、侧重什么，再要你本周/本月实际做的事，写成能直接交的汇报。用户说写周报、写月报、工作总结这类话时使用，比如「帮我写这周的工作周报」「帮我写一份上半年工作总结」「帮我写下个月的工作计划」。只读、只查、只解释的问题不用。"
description_zh: "周报、月报、工作总结、工作计划：先问清给谁看、侧重什么，再要你本周/本月实际做的事，写成能直接交的汇报。"
description_en: "Weekly and monthly reports, work summaries and plans — confirms the audience and focus, collects what you actually did, then writes a report you can hand in."
category: "办公文书"
allowed-tools: mcp__guiputi__match_scene, mcp__guiputi__get_scene_form, mcp__guiputi__submit_fields, mcp__guiputi__confirm_intent, mcp__guiputi__compile_task, mcp__guiputi__report
version: "1.0.0"
author: "硅菩提"
---
# 工作汇报

## 什么时候用

用户要写周报、写月报、工作总结这类事——比如「帮我写这周的工作周报」「帮我写一份上半年工作总结」「帮我写下个月的工作计划」——就用这个技能：先用「硅菩提办事确认卡」连接器出一张确认卡，问清要点和材料，用户确认后按执行协议写成品。

只读、只查、只解释的问题不用它，直接回答。

## 需要的连接器

这个技能靠 WorkBuddy 连接器市场里的**「硅菩提办事确认卡」**提供的六个工具（match_scene / get_scene_form / submit_fields / confirm_intent / compile_task / report）。先调一次 `match_scene` 试探：工具不存在或报未连接，就告诉用户到连接器市场安装「硅菩提办事确认卡」（不填密钥也能试用），**不要退回普通聊天硬写，也不要自己编一张卡**。

## 常办事项

用户的话对上下面某一件，调 `get_scene_form` 时直接带上对应的 `scene_id`，不用先调 `match_scene`。

| 事项 | scene_id | 用户常这么说 |
|---|---|---|
| 周报 | `scene:new-l9yigwg0bv9c` | 写这周周报、汇报本周工作 |
| 月报 | `scene:new-wmxszk8igagw` | 写月报、月度工作汇报 |
| 工作总结 | `scene:workdoc-3` | 季度/半年/年度总结、述职 |
| 工作计划 | `scene:generated-1b3sydd` | 下个月/下季度/明年的工作计划 |

表里没有的，先调 `match_scene`：候选里有明显贴切的就用它的 `scene_id`，没有就把前 3 个列给用户点定，不要替用户猜。

## 工作流程

1. 调 `get_scene_form`，`text` 填用户原话，有 `scene_id` 就带上。WorkBuddy 会把结果渲染成可点选的卡片控件——那是唯一的卡。你的回复不超过两行：一句说要确认什么，一句说怎么操作（`card_url` 不为空时给链接作备用）。返回的 `materials` 里 `required` 的项（本周做了什么、会议记录……）是 AI 补不了的材料，加一句请用户直接发给你。
2. **回传规则**：用户在对话里回答了卡上的任何一项，都要立刻交回——点定做法或回答追问用 `submit_fields`；字段、场景表单和材料写进 `confirm_intent` 的 `field_actions` / `scene_form`。不许只记在对话里自己往下办。
3. 用户说继续、确认、就这样办，或卡片发来「卡片上已经确认好了，继续办」：调 `confirm_intent`。返回 `already_confirmed: true` 正常往下走；返回 `missing` 就补上再确认，用户坚持缺着办才带 `force: true`。
4. 调 `compile_task`（确认后才给的材料放进 `materials {id: 文本}`）。返回 `material_missing` 就问用户要 `missing_materials` 里的东西再调一次；成功后**严格按 `protocol_prompt` 写成品**，成品后单独一段列「AI 假设：」。
5. 交付后调 `report`（`completed`）；用户明确采纳或不要，再调一次（`adopted` / `rejected`）。

## 规矩

- **一次办事只出一张卡**。宿主把卡渲染成控件后，不要再用文字复述卡上的字段，不要自己画卡（自渲染的卡提交不回来，是假通道），不要输出「状态锚点」；卡出来后回复不超过两行：一句说要确认什么，一句说怎么操作。
- **材料闸门**。`get_scene_form` 返回的 `materials` 里 `required` 的项（本周实际做了什么、会议记录、原始数据……）是 AI 补不了的原料，用户没给之前不出成品：先问用户要，随 `confirm_intent` 的 `scene_form` 或 `compile_task` 的 `materials` 交回。`compile_task` 返回 `material_missing` 就照 `hint` 补。禁止用【】占位符把成品糊出来。
- **成品形态以 `compile_task` 返回的 `deliverable` 为准**：文字文案就在对话里给文字，不另外落文件；用户没要文件，不生成文件。
- **候选不猜**：`match_scene` 的候选没有明显贴切的，把前 3 个列给用户点定。
- **日期以今天为准**。卡片或协议里出现的年份、星期如果和今天对不上，按用户说的时间和今天的日历改正，并在成品后提醒用户核对。
- AI 补的内容一律标明「AI 假设：」，单独成段，不能写成用户说过的事实。
- 工具返回末尾的机器可读字段不要念给用户。
- 成品开头不写「好的，以下是……」这类客套话，直接给成品。
- 成品里涉及数字、日期、金额、制度条款的地方，提醒用户自己核对。
- 汇报里的事项、数据、进度只能来自用户提供的材料；材料没给之前不出成品，也不用【】把成品糊出来。
- 没有数字的地方不要编数字，用「完成/进行中/待启动」这类状态词。
