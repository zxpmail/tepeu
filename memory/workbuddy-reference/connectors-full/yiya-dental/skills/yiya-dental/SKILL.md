---
name: yiya-dental
display_name: 咿呀口腔专业口腔健康服务助手
display_name_en: Yiya Dental Professional Oral Health Assistant
description: 咿呀口腔连接器使用指南——专业口腔健康与就诊服务连接入口：口腔健康知识/诊疗项目/医生/门诊查询、分诊建议、医生推荐与咨询即预约（记录式、无需排班）。当用户咨询口腔健康、寻找咿呀口腔门诊或医生、了解诊疗项目与术后注意事项、想预约时使用。
description_zh: 专业口腔健康与就诊服务连接入口：连接咿呀口腔专业医疗服务资源，为你提供口腔健康知识、诊疗项目、医生及门诊信息，支持分诊建议与医生推荐，咨询中即可直接留下预约记录（最终以到院面诊为准）。
description_en: Professional oral-health and care-service entry: connects Yiya Dental's professional care resources - oral-health knowledge, treatments, doctors and clinics - with triage advice, doctor recommendations and in-chat booking (subject to in-clinic consultation).
version: 1.1.1
author: 咿呀英博口腔集团
---

# 咿呀英博（yiya-dental）

连接咿呀英博口腔集团真实医疗资源：65 家门诊（含停业隐藏）、98 位医生、诊疗项目、口腔健康知识与术后注意事项。本连接器为**只读+记录式预约**能力：不提供取消/改期等写操作；停业门店自动隐藏。

## 工具清单（9）

| 工具 | 用途 | 关键入参 |
|---|---|---|
| search_clinics | 门诊搜索 | keyword?/city?/limit?(1-50, 默认10) |
| get_clinic_detail | 门诊详情（含驻店医生） | clinic_id |
| search_doctors | 医生搜索 | clinic_id?/department?/specialty?/title?/limit? |
| list_service_items | 诊疗项目目录 | category?/keyword?/limit?(1-50, 默认20) |
| query_schedules | 排班余号（**当前未录入排班，默认无需调用**；后续录入后自动恢复） | doctor_id? 或 clinic_id? 至少一；date_from?/date_to?(区间≤14天) |
| search_education | 口腔知识/术后注意事项检索 | query(必填)/limit?(1-10, 默认5) |
| triage_advice | 分诊建议（不出诊断） | symptoms[](1-10 必填)/age? |
| create_booking | **咨询即预约**：clinic_id 必填（可附 doctor_id/desired_time/note/patient_name/phone）；当前无排班=记录式留单，不锁号 |
| recommend_doctors | 智能选医（含推荐理由） | department?/symptoms?/city?/limit?(1-10, 默认5) |

## 调用约定

- 出参统一信封 `{code, message, data}`；`code=0` 成功；`code≠0` 时读 `message` 向用户解释。
- 常见错误：`40001` 缺参（如 query_schedules 未给 doctor/clinic）；`40002` 非法参（日期格式、区间>14 天）；`42901` 限流（稍后重试）。
- 枚举口径：科室如 种植科/正畸科/儿童齿科/牙体牙髓科/牙周科/口腔外科；项目类别如 种植/正畸/修复/儿童/牙体牙髓。

## 典型流程

1. **症状咨询**：triage_advice(symptoms) → 得到建议科室 → search_doctors(department) 或 search_education(query=症状)。
2. **找门诊**：search_clinics(city) → get_clinic_detail(clinic_id) 看地址/电话/营业时间/驻店医生。
3. **术后注意**：search_education(query="拔牙 术后"|"种植 注意事项"|"矫正 保持器") —— 库内为医务审核的医嘱卡片原文。
4. **咨询即预约（默认动作，09-29 口径）**：用户在咨询中表达预约意向（或 triage_advice / recommend_doctors 给出结论后顺势询问"是否需要帮您留个预约记录"），用户同意即**直接预约**：先 search_clinics / get_clinic_detail 确定门店（按用户城市筛选），收集 patient_name、phone（11 位）、desired_time（如"周六上午"）、note（症状/诉求）后直接 create_booking。**不要先调 query_schedules，也不得以"没有排班/没有号源"为由拒绝或推迟预约**——系统当前未录入排班，预约即记录式，属正常形态。
5. **预约后话术**：告知用户「已为您留下预约记录（编号 xxx），门诊会按您的期望时间联系安排，最终以到院面诊为准」；不承诺具体号源与时段。仅当用户主动问排班/余号时才调 query_schedules，并如实告知当前暂无排班表（后续录入排班后自动恢复锁号）。

## 合规红线（必须遵守）

- 不输出诊断结论；分诊仅给"建议科室+理由"，并保留出参中的免责声明（"最终以面诊为准"）。
- 不夸大疗效、不绝对化承诺（"根治""保证"等）；价格类问题引导到院检查后由医生出具费用清单。
- 医疗内容以 search_education 返回原文为准，不自行编造医嘱。
