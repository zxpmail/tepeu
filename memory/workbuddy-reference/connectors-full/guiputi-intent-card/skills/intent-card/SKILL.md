---
name: intent-card
display_name: 硅菩提办事确认卡
display_name_en: Guiputi Task Confirmation Card
description: 办事确认卡。用户要动手办一件事时使用，比如帮我写、帮我改、帮我做、帮我发、帮我提交、帮我发布、帮我处理：先用硅菩提连接器出一张确认卡，摆出目标、对象、边界和验收，标明哪些是用户说的、哪些是 AI 补的，只问必须用户拍板的几项和 AI 补不了的材料，确认后再按执行协议写成品。适用于写方案、写合同、写邮件、写通知、写周报、写纪要、做报价、做表格、改文件、发布内容。只读、只查、只解释的问题不用它。
description_zh: 办事前先出确认卡，问清要点和材料，确认后按执行协议写成品。
description_en: Show a confirmation card before doing a task, collect decisions and materials, then write the deliverable from the confirmed protocol.
category: productivity
allowed-tools: mcp__guiputi__match_scene, mcp__guiputi__get_scene_form, mcp__guiputi__submit_fields, mcp__guiputi__confirm_intent, mcp__guiputi__compile_task, mcp__guiputi__report
version: 1.2.0
author: 硅菩提
---

# 办事确认卡

## 什么时候用

出现下面任一信号，先出卡，不要直接开工：

- **动作类**：写、改、发送、提交、发布、购买、删除
- **对外类**：成品要交给本人以外的人（客户、供应商、领导、同事）
- **花钱类**：涉及预算、报价、合同、采购
- **长任务**：预计要 3 个以上步骤

只读、只查、只解释的问题，不出卡，直接回答。

## 六条铁律

1. **一次办事只出一张卡。** WorkBuddy 会把 `get_scene_form` 的结果直接渲染成可点选的卡片控件（带「对，就这么办」按钮），那就是唯一的卡。**不要**用文字把卡上的字段再复述一遍，**不要**自己画卡（widget / SVG / HTML 做的卡没有回传通道，用户点了也提交不回来，是假通道），**不要**输出「状态锚点」之类的东西。卡出来之后你的回复不超过两行：一句说要确认什么，一句说怎么操作。
2. **AI 补的内容一律标明。** 「AI 假设」单独成段，每条前缀「AI 假设：」，不能写成用户说过的事实。
3. **材料闸门。** `get_scene_form` 返回的 `materials` 里 `required` 的项（本周实际做了什么、会议记录、原始数据……）是 AI 补不了的原料。用户没给之前**不出成品**：先问用户要，随 `confirm_intent` 的 `scene_form` 或 `compile_task` 的 `materials` 交回。`compile_task` 返回 `material_missing` 就是被闸门拦下了，照它的 `hint` 补。**禁止用【】占位符把成品糊出来。**
4. **成品形态以 `compile_task` 返回的 `deliverable` 为准。** 「文字文案」就在对话里给文字，不另外落文件；用户没要文件，不生成文件、不弹文件卡片。
5. **必须用户拍板的最多问 3 项**，能自己定的不要问。对外发送、花钱、涉密三类动作，用户不点头不执行。
6. **工具返回末尾那块机器可读字段不要念给用户听。**

## 怎么读工具返回

每个工具都返回一段人话，末尾有一块：

    【给 AI 的机器可读字段，照着往下调，不要念给用户】
    ```json
    { "ok": true, "intent_id": "ito_…", "next_action": "…", "card_url": "https://…" }
    ```

**往下调工具时，从这块 JSON 里取值**，不要从上面的人话里猜：

| 字段 | 出现在 | 用途 |
|---|---|---|
| `intent_id` | 除 match_scene 外全部 | 后面每个工具都要带 |
| `next_action` | 全部 | 下一个该调哪个工具，照着走 |
| `candidates[].scene_id` | match_scene | 调 get_scene_form 时带上 |
| `card_url` | get_scene_form | 能点选的网页卡链接（备用） |
| `materials[]` | get_scene_form | AI 补不了的材料项：id / label / required |
| `fields[]` | get_scene_form | 合同字段的**键名**和填没填：key / label / filled。调 field_actions 只能用这里的 key，别照中文猜 |
| `leaf_options[].leaf_id` | get_scene_form、submit_fields | 用户点定具体做法时用 |
| `trial` | get_scene_form | 免费试用还剩几次、去哪申请密钥 |
| `already_confirmed` | confirm_intent | true 表示用户已经在卡片上确认过，直接往下走 |
| `missing` | confirm_intent | 缺的硬前置字段 |
| `missing_materials` | compile_task | 材料闸门拦下时缺的材料 |
| `protocol_prompt` | compile_task | 执行协议原文，照着它写成品 |

## 工作流

### 1. 找场景

调 `match_scene`，参数 `text` 填用户原话。候选里有明显贴切的就记下它的 `scene_id`；**没有明显贴切的，把前 3 个列给用户点定，不要替用户猜**；没有候选直接进下一步。

### 2. 出卡

调 `get_scene_form`，参数 `text` 填用户原话，有 `scene_id` 就带上。

WorkBuddy 会在对话里直接显示一张可点选的卡片。你的回复只写两行，例如：

> 请在上面的卡片上确认几项，点「对，就这么办」即可。
> 也可以在浏览器里点选：<card_url>（需要时再用）

返回里 `materials` 有 `required` 的项时，在这两行里加一句要材料：「另外请把本周做的事直接发给我（要点、数据都行）」。

返回里有 `trial` 时，说明现在是免费试用；次数快用完时提醒用户到 `trial.keys_url` 免费申请密钥。

### 3. 用户回答后立刻交回（回传规则）

用户在对话里回答了卡上的任何一项——口头回答、或用你自带的选择题回答——**都要立刻交回服务器**，不许只记在对话里自己往下办：

- 用户点定「到底办哪一件」：调 `submit_fields`，带 `leaf_id`。
- 用户回答分歧追问：调 `submit_fields`，带 `clarify`（`dimension` 和 `value`）。
- 用户回答字段或场景表单：调 `confirm_intent`。**键名一律取自机读块的 `fields[]`，不要照卡上的中文自己译**（比如「情况（时间/背景）」的键是 `context`，不是 `situation`）。
  - `filled: false` 的字段（卡上还空着的）：**只能用 `fill`**，`{"context": {"action": "fill", "value": "…"}}`。对空字段用 `edit` 或 `confirm` 会被退回。
  - `filled: true` 的字段：改值用 `edit`，认可原值用 `confirm`，不管用 `skip`。
  - 场景表单答案和材料写进 `scene_form`（材料用 `materials[].id` 做键）。

没交回的答案，编译出来的执行协议里就没有。

### 4. 用户确认

用户说继续、好了、确认、选完了、可以、就这样办，或者卡片自动发来「卡片上已经确认好了，继续办」，都当作已经在卡片上提交完了：**直接调 `confirm_intent`（参数 `intent_id`），不要再问一遍字段。**

- 返回 `already_confirmed: true`：正常，用户在卡片上点过了，往下走。
- 返回 `missing`：用 `field_actions` 把缺的补上再调一次；用户坚持缺着也要办，才带 `force: true`。

### 5. 编译并写成品

调 `compile_task`（参数 `intent_id`；用户在卡片确认后才给的材料放进 `materials {id: 文本}`）。

- 返回 `material_missing`：问用户要 `missing_materials` 里的东西，拿到后放进 `materials` 再调一次；用户明确说没有、坚持先出稿，才带 `force: true`。
- 返回成功：从机器可读字段里取 **`protocol_prompt`**，**由你严格按它写成品**。协议里写着用户确认过的选择、材料、约束、交付形式和写法要求，不要另起炉灶。这个连接器不会在服务器上代写成品。
- 成品后单独一段列出「AI 假设：」，提醒用户核对数字、日期、金额。

### 6. 回执

成品交付后调 `report`（参数 `intent_id`，`event` 填 `completed`）。用户明确说采纳或不要，再调一次 `report`（`adopted` 或 `rejected`）。

## 出错时

- 提示没带密钥、密钥无效：请用户到 https://www.gpt.com.cn/mcp/keys 免费申请或更换密钥，填进连接器设置。
- 提示今天的免费试用次数用完了：同上，申请密钥后就能继续用。
- 工具连不上：告诉用户硅菩提连接器暂时不可用，**不要自己编一张卡，也不要假装已经确认，也不要退回普通聊天硬写**。
