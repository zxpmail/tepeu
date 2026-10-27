---
name: yuanjie-blm
description: Anti-counterfeit label tracing, label stock-flow, customer label-fee quota and outstanding receivables in the Yuanjie brand-licensing system. Use when the user asks where a specific label code went, who claimed it, how much label-fee quota a customer has left, or which customers still owe guaranteed minimums, deposits or unsettled commissions. Read-only.
description_zh: 查防伪标溯源、标码进销存流水、客户标费额度、欠缴应收（保底金/保证金/提成）。只读。
description_en: Trace anti-counterfeit labels, query label stock flow, customer label-fee quota and outstanding receivables. Read-only.
version: 1.0.0
author: 原界
allowed-tools: blm_scenario_arrears_query, blm_scenario_quota_manage, blm_stock_flow_query, blm_trace
---

# 原界品牌授权 · 防伪标与标费

这一批工具**全部只读**，四条，但每条都有一个照着用会出错的地方。

## 🛑 三类欠缴各问各的，不要合在一起报

`blm_scenario_arrears_query` 能查三类：**保底金未付清、保证金待缴纳、销售提成未结算**。

用户说"保底金"就**只查保底金**，说"保证金"就只查保证金。三类混在一起报出来的总额没有任何业务含义，而它看起来像个正经数字——用户会拿去用。

## 🛑 导出 Excel 那条路没开

`blm_stock_flow_query` 用 `action` 区分动作，但**这一期只放了分页查询（`page`）**。导出那个动作没开放——它会在服务端生成 xlsx，而用户在 WorkBuddy 里拿不到那个文件。

用户要导出，就说这一期不支持、让他去原界系统里导，**不要调那个 action**（调了也会被挡回来）。

## 标费额度是三个视图

`blm_scenario_quota_manage` 的 `action` 决定看哪一个视图（按客户查所有合同 + 授权期的可用额度 / 明细 / 汇总）。**先问清用户要的是哪一层**，不要默认一个再让他自己发现看的不是他要的。

## 溯源

`blm_trace` 按**防伪标编码**查这个标码的完整链路——谁申领的、发到哪个门店或仓库、经过哪些操作。

标码要用户给准，**不要按品牌名或批次去猜一个标码**。查不到就说这个标码在系统里没有记录，那本身就是一条有用的结论。

## 涉及客户财务

额度、欠缴、应收都是客户的钱。**答不确定就说不确定，绝不推算。** 数字只能来自查询结果本身。
