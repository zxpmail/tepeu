---
name: yuanjie-workday
description: The user's own work context from Feishu — today's calendar, meetings they attended with AI minutes, group messages that need their attention, pending tasks and approvals, and 1-on-1 preparation. Use when the user asks what meetings they had, what's going on today, what they need to handle, or wants to prepare for a one-on-one with a named person. Read-only; never sends a message or changes a calendar.
description_zh: 查你自己的飞书工作上下文：今天的日程与会议、需要你知道的群消息、等你处理的任务与审批，以及跟某人 1对1 的会前提要。只读，不发消息、不改日程。
description_en: Query your own Feishu work context — calendar, meetings and minutes, group messages needing attention, pending tasks and approvals, and 1-on-1 prep. Read-only.
version: 1.0.0
author: 原界
allowed-tools: yj_meeting_list, yj_morning_brief, yj_one_on_one_prep
---

# 原界工作台 · 你自己的工作上下文

这三把工具取的是**当前用户自己**的飞书数据。全部只读——**发不了消息、改不了日程、建不了任务**。

用户是谁由授权时的身份决定,**不接受在参数里指定别人**。用户说"帮我看看老王今天开了什么会",答"只查得到你自己的"。

## 选哪一把

| 用户问的 | 用 |
| --- | --- |
| 我今天/昨天/这周开了哪些会 | `yj_meeting_list` |
| 今天什么情况 / 开工看一眼 / 帮我理一下今天 | `yj_morning_brief` |
| 等下要跟张三 1对1,帮我准备一下 | `yj_one_on_one_prep` |

## 🛑 三件必须当场说清的事

**① 「没查到」和「没有」是两句话。**

`yj_morning_brief` 有四路取数(日程 / 待处理 / 群消息 / 行业新闻),**某一路失败时它会把失败单独列在末尾**,不会说成"今天没有"。你转述的时候**必须把那几行一起转述**。把"日历读不到"说成"你今天没有安排",用户就照着这句话去安排别的事了。

`yj_one_on_one_prep` 同理:会议纪要**只读得到用户自己建的那些**。"近 30 天没有记录"**不等于**"你们近 30 天没聊过"。这句话工具自己会带在产出末尾,别删掉。

**② `yj_one_on_one_prep` 的 `person` 必须由用户说出来。**

不要自己从今天的日程里挑一个人。飞书日程接口**不返回参会人**(只有标题/起止/地点),靠标题里有没有「1v1」去猜,**漏一场不报错、那个人就凭空消失了**。用户没说跟谁,就直接问他跟谁。

**③ 不做绩效评价。**

`yj_one_on_one_prep` 给的是上下文和两个值得问的问题,**不是对这个人的评价**。别拿它的产出去回答"这个人怎么样"。

## 边界

- **时间范围**默认今天。用户说"这周"就传 `time_range` / `date_from`+`date_to`,别自己换算成一个具体日期再传。
- **全天日程**(出差、团建)在 `yj_meeting_list` 里单列,**不计入会议时长**。用户问"我今天开了几小时会",别把出差那 8 小时算进去。
- **预订会议室、创建会议、改日程、发消息**——这一期都做不了。当场说做不了,**不要说"已经帮你约了"**。
