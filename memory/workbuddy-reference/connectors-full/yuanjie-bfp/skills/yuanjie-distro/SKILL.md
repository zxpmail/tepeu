---
name: yuanjie-distro
description: Transfer orders, real-time inventory, stock movement history and customer credit in the Yuanjie distribution/retail and ERP system. Use when the user asks how much stock a SKU or style has left, what is on a transfer order, a customer's credit limit or balance, or in/out movement over a period. Read-only, and it touches customer financial data.
description_zh: 查调拨单、实时库存、出入库流水、客户授信额度与余额。只读，涉及客户财务数据。
description_en: Query transfer orders, real-time inventory, stock movements and customer credit. Read-only; involves customer financial data.
version: 1.0.0
author: 原界
allowed-tools: distro_retail_check_import, distro_retail_check_location, distro_retail_detail_page, distro_retail_dtl_summary, distro_retail_get, distro_retail_list, distro_retail_loc_soh_items, distro_retail_row_num, distro_retail_sku_codes, erp_customer_finance_query, erp_stock_query_list, erp_stock_transactions_query
---

# 原界进销存 · 调拨 / 库存 / 客户财务

这一批工具**全部只读**。其中有一部分是**客户财务数据**（授信额度、余额、回款），处置标准比别处严。

## 🛑 金额和库存数只能来自查询结果本身

**答不确定就说不确定，绝不推算、绝不估、绝不"大概"。**

- 用户问"他还能赊多少"，去查 `erp_customer_finance_query` 的 `quota` 和 `balance`，**不要拿额度减已用自己算**——口径可能不是你想的那个。
- 用户问"这个款一共多少件"，去查库存，**不要把几个仓的数字自己加起来**除非他明确要合计，而且要说清你加了哪几个。
- 查不到就说查不到。一个编错的金额比一句"查不到"贵得多。

## 调拨单：先抬头，再明细

一张调拨单有四个视角，按问题选，不要一次全拉：

| 问的是 | 用 |
| --- | --- |
| 这张单什么状态、发哪儿、谁收 | `distro_retail_get`（抬头） |
| 一共多少行、通知/发货/收货各多少、总金额 | `distro_retail_dtl_summary`（汇总） |
| 具体哪些商品、每行多少 | `distro_retail_detail_page`（分页明细） |
| 某个 SKU 在第几页第几行 | `distro_retail_row_num` |

🛑 **用户问"一共多少行"就去看汇总，不要靠翻页数数。** 翻到一半断了你就会报一个偏小的数，而且看不出来。

⚠️ `distro_retail_row_num` 的**每页条数必须和用户正在看的列表一致**，否则算出来的页码是错的——而它照样会返回一个数字。

## 其余几条

- `erp_stock_query_list` **直查 DB 不走缓存**，是实时数，但也因此比别的慢一点，不要在一轮里反复调。
- `erp_stock_transactions_query` 的 `summary_type` 决定按什么维度汇总。**先问清用户要按什么看**，不要默认一个再让他自己发现不对。
- `distro_retail_check_import` / `check_location` 是**录单前的校验**，不是查询。用户没在准备导入明细时不要调。
