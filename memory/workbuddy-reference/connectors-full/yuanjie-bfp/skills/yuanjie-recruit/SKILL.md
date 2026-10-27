---
name: yuanjie-recruit
description: Candidate pipeline, job postings and JD, interview scheduling and Offer status in the Yuanjie recruiting system. Use when the user asks how many new resumes came in today, which candidates are pending a decision, what interviews are scheduled this week, a candidate's AI screening score, which jobs are open, or which Offers have gone unanswered. Read-only — it cannot change any status or send an Offer.
description_zh: 查候选人、岗位与 JD、面试安排、Offer 状态。只读，改不了状态也发不出 Offer。
description_en: Query candidates, job postings, interview schedules and Offer status. Read-only — cannot change status or send Offers.
version: 1.0.0
author: 原界
allowed-tools: recruit_candidate_check_duplicate, recruit_candidate_detail, recruit_candidate_interview_list, recruit_candidate_list, recruit_candidate_overview, recruit_candidate_parse_resume, recruit_candidate_stats, recruit_interview_advice, recruit_interview_list, recruit_interview_rounds, recruit_job_complete_jd, recruit_job_detail, recruit_job_list, recruit_job_parse_jd, recruit_job_search, recruit_job_summary, recruit_offer_list
---

# 原界招聘 · 候选人 / 岗位 / 面试 / Offer

这一批工具**全部只读**。你看得到简历、AI 初筛评分、面试记录和 Offer 状态，但**改不了任何状态，也发不出 Offer**。

用户要你"把他推进下一轮""发个 Offer 给他"——直说这一期做不了，告诉他去原界招聘系统里操作。**不要假装做了。**

## 三条会花钱的，一次问题只调一次

`recruit_candidate_parse_resume`、`recruit_job_parse_jd`、`recruit_job_complete_jd` 是**真实消耗算力配额**的 AI 调用，不是查询。

它们标着只读（确实不改库），但每调一次都在烧钱。**同一个问题不要重试、不要为了"再确认一下"调第二遍。** 结果不理想就把结果交给用户，让他决定要不要重来。

## 两个维度看面试，别选错

| 问的是 | 用 |
| --- | --- |
| 今天 / 本周有哪些面试（日程视角） | `recruit_interview_list` |
| 这些候选人各自面到第几轮了（人的视角） | `recruit_candidate_interview_list` |

`recruit_interview_list` **按身份自动过滤**：招聘专员看到自己负责的，面试官只看到自己参与的。所以两个人问同一句话拿到的结果不一样，这是对的，不要解释成数据有问题。

## 空结果不一定是"没有"

`recruit_interview_advice` **结果为空说明 AI 还在生成**（约 10–30 秒），不是"没有建议"。遇到空结果就告诉用户稍等再问，**不要报成"这轮没有面试建议"**。

同样地，`recruit_interview_rounds` 里某轮的评价结论为空 = **那一轮还没评**，不是评价内容是空的。

## 其余几条

- 用户问"在招岗位有哪些"，统一走 `recruit_job_list`，不要用 `recruit_job_search`——后者是发新岗位前检索历史 JD 用的（含已下线），拿来回答"在招哪些"会把下线的一起端出来。
- `recruit_candidate_check_duplicate` 只检测不改状态，查的是 **180 天内同一岗位**的重复投递。
- `recruit_candidate_overview` 一次就能回答"今天招聘什么情况"，不要拆成好几条分别查再自己拼。
- **Offer 里有薪资。** `recruit_offer_list` 能看状态，但生成 Offer PDF / 预览链接那两条**这一期没有开放**——那是可外发的薪资内容。用户要就说没有。
