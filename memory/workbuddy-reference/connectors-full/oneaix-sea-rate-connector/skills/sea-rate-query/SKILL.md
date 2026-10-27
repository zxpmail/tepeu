---
name: sea-rate-query
description: Use when the user asks about 港口五字码、海运费、历史运价、实时运价、船期，或按航线、箱型、船公司进行海运报价比较。
minWorkbuddyVersion: 5.0.0
---

# 海运费查询

使用本连接器的 `cvt_port_to_code` 和 `fetch_sea_rate` 工具，依据实际返回结果回答。工具名称可能带有客户端添加的命名空间，以当前连接器发现的工具为准；调用前核对其输入 schema。

## 连接与能力

- WorkBuddy 所在设备须能访问配置的内网 MCP 地址；当前连接不要求用户填写 Token。认证要求变化时，按服务端要求重新连接。
- 支持港口转换、历史运价查询和实时比价；实时比价会在上游创建查询任务，不代表订舱或下单。
- 当前公开 schema 将 `weight` 声明为吨，但仓库中的下游模型标注为整数公斤，适配层直接透传。带重量的实时查询需先由服务维护者确认并统一单位；在此之前明确告知用户该限制，不擅自换算、忽略用户的重量条件或声称报价已按重量筛选。

## 查询流程

先区分港口查询与运价查询。用户仅查询港口五字码时，只收集待查询的港口名称，按第 2、3 步转换或澄清，展示港口名称、国家和五字码后结束；不要求提供完整航线，也不调用 `fetch_sea_rate`。以下完整流程仅用于运价查询。

1. 提取起运港、目的港、箱型、船公司、出发日期及查询模式。缺少起运港或目的港时先补充；未要求实时查询时使用 `realtime: false`，并在结果中标明历史运价。
2. 用户给出港口名称时，分别调用 `cvt_port_to_code`。只有 `port` 非空、`five_code` 有值且港口与用户意图一致时，才直接使用结果。用户明确提供的五字码可直接传入。
3. `port` 为空或港口存在歧义时，展示相关候选的名称、国家和五字码，请用户确定。`suggest` 和 `shortlist` 是候选，不将最高分当成确定结果。候选全部无关时，请用户补充国家、英文港名或五字码；不从无关候选中选一个继续查价。
4. 调用 `fetch_sea_rate`，参数直接放在 arguments 顶层，不包一层 `params`。历史查询中未提供的可选筛选项省略。实时查询前收集明确的箱型列表与船公司代码列表：当前下游实时任务模型要求这两个列表，不能只因 MCP schema 标为可选就传 `null`。
5. 将明确的相对日期转成 `YYYY-MM-DD`；日期有歧义时先澄清。历史查询的日期是预计离港时间下限，应根据返回船期核对用户日期要求。
6. 汇总实际返回的报价；缺少字段标为“未提供”。无记录时说明当前条件下未查到报价，再建议调整日期、箱型或船公司。

## 工具参数

### cvt_port_to_code

| 参数 | 类型 | 必填 | 含义 |
| --- | --- | --- | --- |
| `port_name` | string | 是 | 非空港口名称，例如“上海” |

返回 `port`（精确匹配或 null）、`suggest`、`shortlist`。港口条目包含 `country`、`en_name`、`zh_name`、`five_code`、`three_code`、`alias`、`score`，字段可为空；当前 `alias` 为字符串或 null。

### fetch_sea_rate

| 参数 | 类型 | 必填 / 默认 | 含义 |
| --- | --- | --- | --- |
| `pol` | string | 必填 | 起运港五字码 |
| `pod` | string | 必填 | 目的港五字码 |
| `box_types` | string[] / null | 默认 null | 箱型，如 `20GP`、`40GP`、`40HQ`；实时查询须提供明确列表 |
| `ship_company_list` | string[] / null | 默认 null | 船公司代码，如 `COSCO`、`MSC`；实时查询须提供明确列表 |
| `weight` | number / null | 默认 null | 仅实时查询；协议声明吨，当前单位限制见“连接与能力” |
| `departure_date` | string / null | 默认 null | `YYYY-MM-DD`；历史查询省略时由服务端默认取当天 |
| `realtime` | boolean | 默认 false | false 为历史运价；true 为实时比价 |

返回对象含 `records` 数组。常用记录字段：

- 航线和船期：`pol`、`pod`、`polEn`、`podEn`、`shipCompany`、`boxType`、`departureTime`、`arrivalTime`、`tripTime`。
- 费用：`stdTotalCost`（总价字符串）及 `currency`；`oceanFreight`（海运费）及 `currencyOcean`；`costList` 为费用详情。
- 舱位、运输方式和有效期：`havePosition`（1 有舱位、0 无舱位）、`travelWay`（"1" 直达、"0" 中转）、`runDate`、`expireDate`、`source`。空值表示未知。

## 调用示例

以下 JSON 是工具 arguments，港口与报价内容不能由示例推断。

`cvt_port_to_code`：

```json
{"port_name": "上海"}
```

用户已明确提供起运港 `CNSHA`、目的港 `DEHAM`，查询 40HQ 历史报价时，调用 `fetch_sea_rate`：

```json
{"pol": "CNSHA", "pod": "DEHAM", "box_types": ["40HQ"], "realtime": false}
```

用户明确要求以上航线的 20GP 实时报价，船公司选择 COSCO 和 MSC，调用 `fetch_sea_rate`：

```json
{"pol": "CNSHA", "pod": "DEHAM", "box_types": ["20GP"], "ship_company_list": ["COSCO", "MSC"], "realtime": true}
```

## 结果呈现与错误处理

- 先说明航线、筛选条件及历史/实时模式，再按“船公司、箱型、出发/到达、总价及币种、海运费及币种、舱位、有效期”列出表格。
- 比价时保持箱型、币种和费用口径一致。总价字符串仅在可明确解析为金额时按数值排序；不同币种分别列出，未提供汇率时不换算。海运费不等同于总价；不把空值当作 0，也不将历史报价描述成当前保证价格或保证舱位。
- 港口转换出现 `invalid response` 等错误时，说明港口转换失败，保留简短错误摘要供维护者定位；不将异常文本中的候选当成已成功验证的结果。
- 网络错误或超时：说明未取得结果，提示检查内网/VPN 与服务状态。实时任务可能仍在执行，不立即重复创建相同查询。
- 401/403：提示检查连接授权；参数错误：根据当前工具 schema 修正；上游 API 或响应校验错误：提示维护者检查服务端，不通过更换港口或编造报价掩盖失败。
- 用户要求订舱、支付或修改订单时，说明本连接器未提供这些能力。
