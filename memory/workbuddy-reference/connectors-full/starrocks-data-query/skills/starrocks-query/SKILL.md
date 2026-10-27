---
name: starrocks-query
description: "查询和分析企业销售经营、销售业绩、库存情况及区域分析数据。"
---

# 森威智能体

使用 `starrocks-data-query` MCP 服务中的 `query` 工具查询 StarRocks 数据。

## 可查询范围

只允许查询以下三张表：

- `baiying_dm.dm_e3erp_ckkcmx_day`
- `baiying_dm.dm_e3erp_lsmx_day`
- `baiying_dm.dm_region_analysis_text_pk`

不要访问其他数据库、catalog、系统库或非白名单表。

## 查询规则

- 只发送单条只读 `SELECT` SQL。
- 不要发送 `INSERT`、`UPDATE`、`DELETE`、DDL、`SHOW`、`DESCRIBE` 或多语句 SQL。
- 表名可带库名前缀，也可省略库名；省略时按 `baiying_dm` 解析。
- 查询应主动添加合理的 `LIMIT`，除非用户明确要求聚合统计。
- 返回结果较大时，优先先做聚合、过滤或分页查询。

## 示例

查询库存明细：

```sql
select *
from baiying_dm.dm_e3erp_ckkcmx_day
limit 10
```

查询零售明细：

```sql
select *
from baiying_dm.dm_e3erp_lsmx_day
limit 10
```

查询区域分析数据：

```sql
select *
from baiying_dm.dm_region_analysis_text_pk
limit 10
```

按日期统计零售数量：

```sql
select dt, sum(sl) as total_sl
from baiying_dm.dm_e3erp_lsmx_day
group by dt
order by dt desc
limit 30
```
