---
name: "guiputi-leave-cert"
display_name: "请假与证明"
display_name_en: "Leave & Certificates"
description: "请假条、在职证明、收入证明、离职证明、入职须知、离职交接清单：问清对象和用途，按规范格式写成能直接打印的文书。用户说请假条、在职证明、离职证明这类话时使用，比如「我下周二要请一天事假，帮我写个请假条」「同事办签证要在职证明，帮我开一份」「帮我写一份离职证明」。只读、只查、只解释的问题不用。"
description_zh: "请假条、在职证明、收入证明、离职证明、入职须知、离职交接清单：问清对象和用途，按规范格式写成能直接打印的文书。"
description_en: "Leave requests, employment/income/resignation certificates, onboarding notes and handover checklists — confirms the recipient and purpose, then writes print-ready documents."
category: "办公文书"
allowed-tools: mcp__guiputi__match_scene, mcp__guiputi__get_scene_form, mcp__guiputi__submit_fields, mcp__guiputi__confirm_intent, mcp__guiputi__compile_task, mcp__guiputi__report
version: "1.0.0"
author: "硅菩提"
---
# 请假与证明

## 什么时候用

用户要请假条、在职证明、离职证明这类事——比如「我下周二要请一天事假，帮我写个请假条」「同事办签证要在职证明，帮我开一份」「帮我写一份离职证明」——就用这个技能：先用「硅菩提办事确认卡」连接器出一张确认卡，问清要点和材料，用户确认后按执行协议写成品。

只读、只查、只解释的问题不用它，直接回答。

## 需要的连接器

这个技能靠 WorkBuddy 连接器市场里的**「硅菩提办事确认卡」**提供的六个工具（match_scene / get_scene_form / submit_fields / confirm_intent / compile_task / report）。先调一次 `match_scene` 试探：工具不存在或报未连接，就告诉用户到连接器市场安装「硅菩提办事确认卡」（不填密钥也能试用），**不要退回普通聊天硬写，也不要自己编一张卡**。

## 常办事项

用户的话对上下面某一件，调 `get_scene_form` 时直接带上对应的 `scene_id`，不用先调 `match_scene`。

| 事项 | scene_id | 用户常这么说 |
|---|---|---|
| 请假条 | `scene:write-leave-request` | 请事假、病假、年假、婚假 |
| 证明 | `scene:write-certification-doc` | 开在职证明、收入证明、居住证明 |
| 离职证明 | `scene:fn-57` | 开离职证明、解除劳动合同证明 |
| 入职须知 | `scene:leaf-xhxprf` | 新同事入职要知道的事 |
| 离职交接清单 | `scene:leaf-199635t` | 离职交接表、工作/资产/账号交接 |

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
- 证明类文书写上「仅用于办理某事，他用无效」，公章位置留空，提醒用户盖章前核对个人信息。
- 姓名、身份证号、工号、入职日期等只按用户给的写，没给就留【】，不要编造。
