---
name: yuanjie-hr
description: Attendance, leave balance, employee records, contract expiry, headcount and business-trip queries in the Yuanjie HR system. Use when the user asks about who is absent today, how many annual-leave days they have left, this month's attendance exceptions, overtime detail, someone's职级/入职日期, whose labour contract is expiring, a department's headcount gap, or their own business-trip requests. Read-only.
description_zh: 查考勤异常、假期余额、今日出勤、员工档案、合同到期、编制与出差单。只读。
description_en: Query attendance exceptions, leave balances, today's attendance, employee records, contract expiry, headcount and business trips. Read-only.
version: 1.0.0
author: 原界
allowed-tools: yj_attendance_status, yj_overtime_detail, attendance_anomaly_plan, attendance_leave_balance, attendance_overtime_list, attendance_overtime_plan, employee_dept_members, employee_search, hhr_attendance_exception_query, hhr_attendance_leave_balance_query, hhr_attendance_status_query, hhr_employee_contract_expiry_query, hhr_employee_employee_archive_query, hhr_org_staffing_query, travel_list
---

# 原界人事 · 考勤与员工档案

这一批工具**全部只读**。你看得到考勤异常、假期余额、员工档案和编制，但改不了任何一条记录，也提交不了任何单据。

## 这一期做不了的事，要当场说

用户最常要求的三件事——**提交补卡、提交调休、发起请假**——这一期都做不了。

`attendance_anomaly_plan` 返回的异常明细里**自带系统建议的处置方案**（补卡 / 调休 / 请假），这很容易让人以为下一步就能提交。不能。把建议念给用户听，然后告诉他去原界人事系统里提，**不要说"已经帮你提交了"**，也不要停在那儿等他确认。

## 查自己和查别人是两条路

- **查自己不需要任何权限**，系统按登录身份自动认人。用户说"我还有几天年假"，直接调，不要先问他工号。
- **查他人、查部门要走权限位。** 被拒绝时**如实说是权限不够**——不要改口说"查不到这个人"。那两句话对用户的下一步动作完全不同：一个是去找管理员，一个是去核对名字。

## 🛑 查本月打卡 / 加班明细，用 `yj_` 那两把

| 用户问的 | 用 | 别用 |
| --- | --- | --- |
| 我这个月打卡情况 / 缺卡几次 / 迟到了吗 / 某天的打卡记录 | `yj_attendance_status` | ~~`attendance_anomaly_plan`~~ |
| 我的加班明细 / 加了几天班 / 超过 7 点的加班 | `yj_overtime_detail` | ~~`attendance_overtime_plan`~~ |

**为什么不能拿 `attendance_anomaly_plan` 当查询用**：它是【处置规划】接口，对旷工/缺卡的天数会**用排班时间兜底**（比如旷工那天填 09:00~18:00）。那不是真实打卡时间。而且 **旷工 ≠ 全天没打卡**——员工可能上午缺卡、下午打了。拿它的数字回答"我几点打的卡"，答出来的是编的，且它不报错。

真实打卡时间以 `yj_attendance_status` 返回的 `onDutyCard` / `offDutyCard` 为准，**字段为空才是真的没打卡**。

同理 `attendance_overtime_plan` 一调用就构造提交，不是查询。用户说"我要看明细就好，不要提交"时尤其别碰它。

## 两套考勤，按问题选

| 问的是 | 用 |
| --- | --- |
| 我的考勤异常 / 我的加班明细 / 我的假期余额 | `attendance_*` |
| 某个员工或某个部门的异常、余额、今天谁没来 | `hhr_attendance_*` |

`hhr_attendance_status_query` 用 `mode` 区分三类问题：今天谁没来、哪个部门缺人、现在店里有多少人。问哪一类就给哪一个 mode，不要三样都拉一遍再自己挑。

## 其余几条

- `employee_search` 支持**模糊和同音**（"张伟"能匹配到"张玮"）。命中多条时列出来让用户挑，不要自己选第一条。
- `hhr_employee_contract_expiry_query` 既能查单人也能查"最近有哪些人合同要到期"，后者是 HR 的高频问题。
- `hhr_org_staffing_query` 回的是编制数、在职数、缺编、超编。**缺编数是算出来的，不要自己减。**
- 假期余额**带单位**（天 / 小时），念数字时把单位一起念——"还剩 3"和"还剩 3 小时"差很远。
