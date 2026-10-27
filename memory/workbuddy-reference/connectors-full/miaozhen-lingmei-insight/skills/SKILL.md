---
name: miaozhen-lingmei-insight
source: miaozhen-lingmei-insight
description: "Miaozhen Lingmei Insight: full-web social analytics — voice & engagement trends, hot topics, audience profiling, category ranking, and multi-platform hot-search rankings."
description_zh: 秒针聆媒洞察：提供社媒声量、互动、趋势、话题、发帖人群画像、子品类排行与热搜查询；各工具支持的平台和指标不同。
description_en: "Miaozhen Lingmei Insight: full-web social analytics — voice & engagement trends, hot topics, audience profiling, category ranking, and multi-platform hot-search rankings."
version: 1.1.1
display_name: 聆媒洞察工具使用说明
examples_zh:
  - 分析防晒品类近两周在抖音的声量与情感
  - 查一下抖音最近的热搜榜
  - 防晒品类近两周在抖音的声量趋势是怎样的
examples_en:
  - Analyze the sunscreen category's voice and sentiment on Douyin in the past two weeks
  - Show me the latest Douyin hot-search ranking
  - What is the sunscreen category's voice trend on Douyin over the past two weeks?
---

# 聆媒洞察连接器

使用秒针社媒数据查询热搜、声量、互动、情感、话题、发帖人群与子品类排行。各接口支持的平台、指标和时间范围不同，先按下表选择工具，再读取该工具的参数定义。

本说明不提供电商销量、销售额或成交金额排行，也不将社媒声量或互动数解释为销售额。用户提供其他数据文件时，按其文件和规则继续处理；本连接器的查询限制不约束文件分析。

## 连接与授权

连接器 source 与 MCP 服务名称均为 `miaozhen-lingmei-insight`。在当前会话中发现并加载所需工具后调用，不猜测工具前缀。

- 找不到所需工具：说明当前环境未挂载该工具，引导安装或启用「聆媒洞察」；已安装则检查当前会话是否已加载工具。工具缺失不等于 Key 无效。
- 工具返回未授权或未绑定：引导在 WorkBuddy「填写 Key」卡片或连接器配置窗口中填写 DataTap Token，不在对话里收取凭证。
- 没有 Token：前往 https://datatap.deepminer.com.cn 注册，新用户赠 200 积分；在 https://datatap.deepminer.com.cn/console/tokens 生成 Key。需人工协助可联系 tianxiufan@miaozhen.com，并注明来自 WorkBuddy。
- 配置字段是 `DATATAP_TOKEN`，由连接器注入请求头。不要把真实 Key 写入查询示例、文件或回复。

## 按需求选择工具

| 需求 | 工具 | 平台与调用范围 |
|---|---|---|
| 将品牌或品类名称匹配为标准标签 | `match_best_tag` | `tag_type` 为品牌标签或品类标签；`tag_names` 只放同一对象的名称变体，不混入上下级品类 |
| 多平台热搜榜 | `query_rank_list` | `source` 为微博、抖音、百度或知乎；每次一个站点，不需要先匹配标签 |
| 声量、互动、情感或内容结构统计 | `query_analysis_data` | 使用 `datasource` 指定数据源；按实际参数选择指标，最多 3 个分组维度 |
| 多品牌社媒表现对比 | `match_best_tag` + `query_analysis_data` | 对各品牌分别匹配与查询，保持同平台、同时间、同指标；品牌对比不是子品类排行 |
| 整体声量与互动概览 | `social_statistic_overview` | 使用 `datasource`；读取返回的总量与同环比指标 |
| 声量、互动随时间变化 | `social_statistic_trend` | 使用 `datasource`；`dimension` 为 `hour`、`date`、`week` 或 `month` |
| 讨论集中在哪些话题 | `social_statistic_hot_topic` | 使用 `datasource`；需要原帖证据时再调用 `query_raw_posts` |
| 讨论内容与口碑归纳 | `query_raw_posts` | 使用 `datasource`，限定时间和返回数量；原帖用于归纳，交付聚合结果及转述观点 |
| 子品类排行 | `social_statistic_category_rank` | 仅抖音、小红书；传 `category` 和 `medias`；时间至少 3 个月、最多 1 年，不返回品牌销售排行 |
| 发帖人群画像 | `social_statistic_user_profile` | `media` 仅小红书、抖音或微博，每次一个；这是发帖账号构成，不是消费者或触达受众画像 |

使用 `datasource` 的工具支持 `平台__站点` 或仅平台名称的数组，例如 `['短视频__抖音']`、`['小红书']`、`['微博']`。仅平台名称表示该平台下全部站点；用户指定单站点时应限定到该站点。其他数据源按所选工具的参数说明确认，不将连接器整体覆盖范围视为每个工具都支持的范围。

## 查询规则

1. **明确范围**：沿用用户指定的对象、平台与时间。未指定时间时，热搜默认近 7 天，声量、趋势、话题和画像默认近两周，子品类排行默认近 3 个月；未指定平台时先询问。将相对时间换成实际日期，`start_time`、`end_time` 使用 `YYYY-MM-DD HH:MM:SS`，单次跨度不超过 1 年。
2. **匹配对象**：品牌或品类查询先调用 `match_best_tag`。支持标签模式的工具传 `target_type="tag"`、`tag_type` 和工具返回的标准名 `name`。主题探索可以使用 `target_type="keyword"`，须填写 `name` 及对应关键词参数。标签匹配失败时说明情况；改用关键词的结果需明确对象口径不同，品类榜不能改成关键词查询。`analysisTargetRule` 需要另外配置魔方 Pro 凭证，不作为普通 Key 的默认调用方式。
3. **控制查询量**：逐平台、逐品牌串行查询。原帖默认先取 100 条；用户指定数量时遵守接口上限。不要因为超时自行改平台、时间或过滤条件。
4. **品类榜层级**：查一级或二级品类的子品类榜单时直接传标准名；查三级品类的排名与表现时，使用其标准名中的二级父类查询，再从返回列表定位目标项。三级品类直接查询可能返回空数组，不代表它没有数据。默认按原始互动数降序重新排名，不能将接口数组顺序当作名次；用户明确指定其他指标时按其要求。注明父类范围、平台、时间和排序指标。查询不足 3 个月时保留用户截止日期，提出扩展到 3 个月的方案，确认后执行。
5. **解释指标**：占比及增长率的小数值转成百分比；不同工具的情感指数定义可能不同，按对应工具和返回字段解释，不统一套用某个取值范围。缺失、非数值和空结果不补成零。声量与互动的排名分别核对，不能由一个指标领先推断其他指标也领先。
6. **交付结果**：先结论，再列聚合指标表及必要的统计口径。多品牌份额注明分母是参与对比品牌的合计；样本口碑不能表述为全量结论。不要输出原帖全文、链接、用户昵称或 ID 等可定位信息。

## 查询示例

以下日期仅说明调用格式。实际调用使用用户确定的时间范围；标签名称必须以匹配结果为准，示例不包含查询结果或演示数值。

### 示例一：抖音防晒品类声量与情感

用户问题：“分析防晒品类近两周在抖音的声量与情感。”

先调用 `match_best_tag`：

```json
{"tag_type":"品类标签","tag_names":["防晒"],"requirement_desc":"分析防晒品类在抖音的声量与情感"}
```

若返回标准名“美妆护肤-面部护理-防晒”，调用 `query_analysis_data`：

```json
{"target_type":"tag","tag_type":"品类标签","name":"美妆护肤-面部护理-防晒","start_time":"2026-09-29 00:00:00","end_time":"2026-10-12 23:59:59","datasource":["短视频__抖音"],"metrics":["声量","互动数","情感指数"],"dimensions":["内容情感"]}
```

### 示例二：抖音热搜榜

用户问题：“查一下抖音最近的热搜榜。”默认近 7 天，调用 `query_rank_list`：

```json
{"source":"抖音","start_time":"2026-10-06 00:00:00","end_time":"2026-10-12 23:59:59","size":100}
```

### 示例三：抖音防晒品类声量趋势

用户问题：“防晒品类近两周在抖音的声量趋势是怎样的？”按示例一匹配标签后，调用 `social_statistic_trend`：

```json
{"target_type":"tag","tag_type":"品类标签","name":"美妆护肤-面部护理-防晒","start_time":"2026-09-29 00:00:00","end_time":"2026-10-12 23:59:59","datasource":["短视频__抖音"],"dimension":"date"}
```

如需查询防晒在面部护理子品类中的排名，应改用 `social_statistic_category_rank`，传父类“美妆护肤-面部护理”、`medias=["抖音"]` 和至少 3 个月的时间范围，不能沿用上述两周窗口。

## 查询失败与无数据

- 超时：同参数重试一次；仍超时则说明本次查询失败并停止重试。调整统计范围或粒度需要用户同意。
- 参数错误：对照当前工具参数定义修正后重查，不通过修改用户指定的平台或时间来掩盖错误。
- 空结果：核对标签、平台、时间和品类层级；若误将三级品类传入品类榜，改查父级一次。仍为空时说明该范围本次未返回数据；父级列表没有目标项时不推断其数值或名次。
- 未授权：按前述连接与授权流程处理。说明一次后等用户配置，不反复输出开通文案。正常返回时直接呈现结果，不主动添加“真实/演示”声明。
- 本连接器说明不附演示数据。若当前专用技能自带演示文件，仅在用户明确要求看效果时按该技能的演示规则使用并标注；不能为填补查询失败而自编数据。
