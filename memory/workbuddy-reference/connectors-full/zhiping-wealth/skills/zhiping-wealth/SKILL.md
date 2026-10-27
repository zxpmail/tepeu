---
name: zhiping-wealth
description: Use the unified Zhiping Wealth MCP for personal product queries and, after enterprise entitlement is verified, enterprise data discovery, queries and server-side exports.
description_zh: 使用统一智评理财 MCP；个人账号查询产品与详情，企业资格验证通过后还可进行企业数据发现、查询和服务端导出。
description_en: Use the unified Zhiping Wealth MCP for personal product queries and entitled enterprise data workflows.
version: 0.5.11
author: 智评理财
---

# 智评理财统一 MCP

本 Skill 只编排当前 Host 已连接的 `zhiping-wealth` MCP。手机号和短信验证码只在浏览器 OAuth 授权页输入，不要求用户在聊天中提供，也不读取本机凭证、直连 MySQL/ES、执行任意 SQL 或用客户端拼接全量文件。


## 用户回答展示规则（强制）

工具返回的字段键、代码和技术空值只用于内部解析，不能复制到用户回答、表格标题、括号注释、脚注或代码块中。只展示中文业务名称和业务释义，禁止原始 JSON、数据库字段名、内部枚举值及“字段=代码”；登记编码、销售代码等用户用于识别产品的业务编码仍可展示。

- `actual_statistic_date` 只写“净值统计日”，`open_type` 只写“开放类型”，不在括号里附英文键或数字代码。
- `null`、`None`、缺失值及空字符串统一写“暂无数据”，不能写“暂无（null）”“无（None）”或填成 0。真实数值 0 按实际单位保留。
- 枚举只用服务返回的业务释义。只有数字代码、没有释义时写“暂无数据”，不得猜测 `open_type=4` 的含义。
- 最短持有期、开放类型及赎回频率分别展示。“每日可赎”只能依据实际返回的每日赎回窗口/规则，不从开放类型代码或一日最短持有期推导；仍需保留确认/到账限制。
- 输出前检查正文、表格和括号注释，删除内部字段名、枚举代码和技术空值；保留有效业务日期、单位、收益口径和风险限制。

正确展示示例：

| 项目 | 内容 |
| --- | --- |
| 近7日年化收益率 | 暂无数据 |
| 近7日累计收益率 | 暂无数据 |
| 万份收益 | 暂无数据 |
| 净值统计日 | 暂无数据 |
| 最短持有期 | 1天 |
| 开放类型 | 暂无数据 |

示例开放类型暂无释义；若服务实际返回中文释义才展示该释义，若有赎回规则另列“赎回频率”，不自行补成每日可赎。

## 首次调用与身份路由

每次会话开始、重新授权、换网络/设备或服务升级后，先调用无参数工具 `get_zhiping_account_capabilities`。服务端每次请求都会实时校验身份：只要账号匹配 `business_user`（企业员工身份），无论其最初是否个人用户，一律按企业账号定位；未匹配企业身份才按个人账号处理。以本次能力探测返回值和实时 `list_tools` 为准，不凭历史会话或用户措辞判断身份：

- `account_type=personal`：只使用个人工具；个人成功查询按每日积分计费。
- `account_type=enterprise` 且 `enterprise_access=available`：只使用 `available_tools` 中的 `zhiping_*` 企业工具，个人工具（`search_wealth_products`、8 个详情工具及 `get_my_credit_balance`）已被服务端停用，不得调用、也不得用个人工具模拟企业查询。企业工具遵循企业策略、租户数据范围、配额及审计，不扣个人积分。
- `account_type=enterprise` 且 `enterprise_access=restricted`：账号仍是企业账号，但企业工具权限尚未开通或当前不可用（`restriction_code` 为受限原因）；此时服务端允许回退使用 `available_tools` 中列出的个人工具（按个人积分计费），首次使用前必须按「企业权限未开通时的使用确认」向用户说明并征得同意，不得猜测、枚举或尝试未暴露的企业工具。`business_user` 的企业身份不因企业授权或策略暂不可用而降级显示成个人账号。
- 个人转企业（或企业授权状态变化）后，下一次能力探测即按新身份返回工具集合；发现 `available_tools` 与之前不同或调用返回 `PERSONAL_TOOLS_DISABLED` 时，重新探测并以新结果路由，不沿用旧工具清单。
- 任何工具名、参数 Schema 与本文冲突时，以本次连接实际发现的 Schema 为准；工具未出现即视为当前身份不可用。

能力探测不扣积分。不要每次普通查询都重复探测；身份、连接或可用工具发生变化时再调用。

### 企业权限未开通时的使用确认

能力探测返回 `account_type=enterprise` 且 `enterprise_access=restricted` 时，先向用户说明身份与状态，再征询是否改用个人工具：

1. 告知身份与状态："您是企业用户（{company_name}，来自能力探测的 `company_name`），目前企业工具权限尚未开通。"
2. 告知开通途径："如需使用企业数据工具，请联系客户经理开通企业权限。"
3. 给出替代选项："您也可以选择使用个人理财产品查询工具继续查询（按个人积分计费）。"
4. 询问："是否使用个人工具继续本次查询？"

用户明确确认后才调用个人工具；确认前不得调用，用户拒绝则停止查询，等待开通企业权限或改问其他问题。同一会话内确认一次即可，后续个人工具查询不必重复询问；用户明确表示只想用企业工具时不再重复推荐个人工具。若用户问题超出个人工具能力范围（如货架、导出、跨产品统计等企业专属数据），如实说明需开通企业权限后才能查询，不要用个人工具硬答，也不要编造企业数据。

## 个人升级企业账号

个人用户提出“升级企业用户”“开通企业版”“联系商务”等意图时，直接告知联系渠道：请联系客户经理，电话 13436936203。个人账号需升级为企业资格后才能使用企业工具/白名单与导出能力；在升级确认完成前，不把个人身份当作企业用户处理，也不承诺企业数据已开放。能力探测仍以实时返回的 `account_type` 和 `enterprise_access` 为准。

## 问题记录

每次实际业务工具调用，都把用户本轮原始问题原样放入可选参数 `user_question`；同一问题后续的分页、轮询或幂等重试继续使用原问题，不要编造或改写。该字段只用于服务排障与用途分析，不参与查询条件、企业授权或计费，也不要放入验证码、手机号、OAuth 令牌、密码、密钥等凭证。若实时工具 Schema 没有 `user_question`，说明当前服务版本不支持记录，正常执行查询即可。

当前 WorkBuddy 会话 ID（由客户端在加载 Skill 时替换）：${CODEBUDDY_SESSION_ID}

每次业务工具调用，将上面已经替换的当前会话 ID 原样放入可选参数 `conversationId`（最长 256 字）；同一会话的后续提问沿用它。若上面仍是变量占位符字面量、为空，或实时工具 Schema 没有 `conversationId`，则省略此参数。不要用本轮请求 ID、`request_key`、MCP 传输会话 ID 或自编标识代替。该参数只用于关联用户问题记录，不能作为认证依据。

能力探测工具不接受 `user_question` 或 `conversationId`。只有实际调用 MCP 业务工具时服务端才能看到这些字段；Host 未传入时，服务端无法从聊天界面自行读取原始问题或会话 ID。

路由依据只能来自能力探测结果和本次 `list_tools`，不能根据用户措辞猜测身份。个人账号只能调用个人工具；`enterprise_access=available` 的企业账号只能调用实际可见的 `zhiping_*` 企业工具（个人工具已停用）；`enterprise_access=restricted` 的企业账号只能调用探测返回的个人工具，且首次使用前需按「企业权限未开通时的使用确认」征得用户同意。任一路径工具不可用时，不得改用另一路径的工具批量拼接来模拟结果。


## 机构名称匹配边界

用户直接点名发行机构时，默认精确匹配机构全称或简称：个人路径使用 `issuer eq`，企业机构检索使用 `zhiping_search_institutions(keyword="工银理财", match_mode="exact")`。只有用户明确要求“名称包含”“模糊匹配”等范围时，才使用个人 `contains` 或企业 `match_mode="contains"`。不把机构名当成产品名称片段搜索；不得把“工银理财”自动扩大到“高盛工银理财”，也不按母行、集团、合作关系或同名片段补充其他机构。精确无匹配时直接说明本次机构未匹配，不自动改为包含查询。例句“上周到期的工银理财产品中，有多少达到了业绩比较基准要求？”只查询精确匹配的工银理财机构；该问题没有授权扩大机构范围。机构类型列举仍可 keyword 留空，不附加名称条件。

## 产品编码辨别

用户只给出一个产品编码时，先去除编码首尾空白，再按完整格式辨别字段：只有符合 `^[CZ][0-9]{13}$`（大写 `C` 或 `Z` 加 13 位数字，共 14 位）的值才是理财产品登记编码，传入 `product_code`；其他任何格式都按发行机构销售代码处理，传入或筛选 `sale_code`。必须完整匹配，不能仅凭首字母、长度或用户笼统称其为“产品代码”来猜测，也不能把不符合格式的值放进 `product_code` 试错。

个人路径收到单独的登记编码时，先调用 `search_wealth_products`，使用 `filters=[{"field":"product_code","op":"eq","value":"用户原始编码"}]` 精确定位；收到单独的销售代码时同样先搜索，使用 `filters=[{"field":"sale_code","op":"eq","value":"用户原始编码"}]`。搜索唯一命中后，再用结果的 `sub_product_id`（作为详情输入 `sub_id`，优先）或登记编码与销售代码组合调用详情工具。搜索无结果时如实说明未找到；命中多个产品或份额时展示候选并请用户选择，不猜测。用户同时给出多个编码时逐个独立判别，不因其中一个符合登记编码格式而把其他编码强行归为同类。

## 个人账号路径

用户提供完整产品名称时，原样提取名称并使用 `product_name eq`；例如“中银理财-乐享天天45号”完整保留，不删“中银理财-”前缀、不改成简称、不擅自认定口语名称。只有用户明确使用简称、片段或目标存在歧义时才使用 `product_name contains` 一轮候选搜索，仍保留期次/编号。只提供“系列名+期次”而没有完整登记名称也属于片段，例如“丰利乐享第373期”必须使用 `product_name contains`，不能因为期次明确就使用 `eq`。`product_name` 同次业务调用匹配母/子产品；明确母/子维度才使用 `parent_name/sub_name`。`contains` 使用中文短语分析及未分词字段的字面匹配；服务端在同一次查询中兼容“第”紧邻阿拉伯数字的两种写法（如“第373期”与“373期”），保留数字。该兼容不进行 fuzzy、编辑距离、关键词改写或相似产品重试；模糊候选先确认。精确空结果只说明本次名称未匹配，不猜测是口语差异或产品不存在。实时 Schema 缺少新字段时需同步升级服务及 Skill，不发送未知字段。

个人产品筛选和所有个人详情请求都先使用 `search_wealth_products` 定位产品，再查询 8 类详情。用户给出机构、系列、期次/编号等足以唯一指向产品的信息且已确认是完整登记名称属于精确搜索路径：默认使用 `product_name eq` 同次定位母/子产品（明确母/子维度时才使用 `parent_name/sub_name eq`），不得擅自拆词、删期次或改查相似名称。搜索唯一命中且 `meta.resolution.next_action=use_unique_match` 后，使用结果的 `sub_product_id`（作为详情输入 `sub_id`，优先）调用目标详情工具；禁止把用户原始名称或编码直接交给详情工具试查。精确搜索返回 `items=[]` / `meta.resolution.next_action=stop_no_match` 时，直接向用户说明没有找到该产品并结束本轮；不得再换关键词、去掉编号、查询同系列或相似名称，也不得自动重复调用。只有用户随后明确要求扩大范围时，才能发起新的模糊搜索。

这是一条固定的两阶段流程：第一阶段搜索负责确认“是哪一个产品/份额”，第二阶段详情负责确认“该产品有没有目标数据”。搜索和详情各自按配置计费，不得因为搜索结果已经带有摘要而跳过用户明确要求的详情查询，也不得对同一条件重复搜索。

用户输入的信息不足以唯一确定产品（如只有产品系列简称、产品名称片段或泛称）时，默认使用 `product_name contains`（明确母/子维度时才使用对应字段） 只搜索一轮，`page_size` 不超过 10。把该轮结果中的产品/份额名称、发行机构、登记编码、销售代码和 `sub_product_id` 作为候选展示，请用户选择具体产品；即使只命中一个候选，也先让用户确认。`meta.resolution.candidate_confirmation_required=true` 或 `next_action=ask_user_to_select` 时，确认前禁止调用详情工具、自动翻页、替换关键词或继续检索。用户确认后只使用所选候选的 `sub_product_id`（作为详情输入 `sub_id`，优先）或登记编码与销售代码组合调用所需详情工具。模糊搜索无结果时同样直接说明未找到，不进行第二轮猜测。

产品名称重名或份额不唯一时，要求补充 `sub_product_id`（详情输入 `sub_id`）或登记编码与销售代码，不猜测份额。个人工具没有下载功能，每次只读取一页；仅当用户明确要求继续时使用 `next_cursor` 取下一页，不自动抓取全部分页或在 Host 侧聚合大结果。重试同一计费请求时复用 `request_key`，新请求使用新值。

搜索结果返回企业 21 个基础字段并回传 `product_status`（产品生命周期状态中文标签），不含业绩比较基准或年化收益摘要。向用户比较候选时，展示实际返回的名称、发行机构、风险等级、产品状态、开放类型、币种和最短持有期；需要业绩比较基准或年化收益时，按指定产品调用对应详情工具。`product_status=在售` 是生命周期状态口径，可确认在售/存续等，但不代表 App 货架当前可购买，不得描述为货架可购状态。

产品已通过 `sub_id`、登记编码或完整名称精确解析，但 `get_wealth_product_performance` 返回 `items=[]`、对应 `product_statuses[].annualized_return_status=no_data`，或 `meta.resolution.next_action=stop_no_return_data` 时，含义是“产品已找到，但当前没有收益率数据”。回答不能只丢出错误式短句：先按 `meta.resolution.resolved_products` 或 `product_statuses[].product.name` 列出实际命中的产品名称，再自然说明该产品暂无收益率数据。如果 `recently_established=true`，同时展示 `establishment_date`，说明“该产品成立时间较近，目前还没有形成可用的收益率数据”；如果该标志为 false 或成立日期缺失，只说暂无收益率数据，不自行猜测原因。推荐句式：“已找到产品：{产品名称}（成立日期：{日期}）。该产品成立时间较近，目前还没有可用的收益率数据。”

上述情况仍是终止态：不得回到产品搜索，不得改查名称相近、同系列、其他期次或其他份额的收益率。即使同一次响应含有最大回撤记录，只要收益率记录缺失或七个收益率字段全部为 `null`，也按收益率无数据处理。只有用户随后明确指定另一个产品时，才能查询另一个产品。

期限和流动性是候选产品筛选依据。用户给出“最多持有 30 天”“至少 1 年”等明确期限时，将天数转换为 `min_holding_days` 的 `lte` 或 `gte` 条件；用户只说“短期”“长期”时，先询问可接受的最大或最小持有天数，不自行设定阈值。用户说“1 天可赎回”时转换为 `min_holding_days lte 1` 筛选最短持有期不超过 1 天的候选产品；搜索不再返回 `one_day_redemption_candidate` 字段，不得声称候选支持申请后 1 天内确认或到账，实际时效以赎回确认规则和销售渠道为准。

向用户比较候选产品时，先执行其风险等级、期限和机构等硬条件，再说明搜索实际返回的开放类型、币种与最短持有期；产品状态、赎回规则、历史年化收益及业绩比较基准需调用对应详情工具后说明。不要只按收益率排序，不要把产品称为“最适合买入”；若用户没有提供风险承受能力或期限边界，只给事实比较并询问偏好。搜索支持按 `sort_by`（`risk_level`、`min_holding_days`、`actual_start_date`、`actual_end_date`、`establishment_annualized`=成立以来单利年化收益率）与 `order`（`asc`/`desc`，默认 `asc`）排序。回答“最高收益”时可直接用 `sort_by=establishment_annualized&order=desc` 按 ES 索引里的成立以来单利年化排序（字段 `establishmentAnnualizedSimpleReturn`），无需再调性能详情；其余收益率口径（月/季/半年/年/今年）仍需先筛选定位候选，再调用 `get_wealth_product_performance` 获取年化收益后比较。

个人工具、字段及最新口径见 @references/tools.md。结果中的实际数据日期、业务口径和单位必须保留；不得向用户展示物理表名、索引名或内部数据源标识；`synthetic-demo` 必须明确说明为模拟数据。精确详情的 `NOT_FOUND` 按上述终止规则说明没有可访问数据；其他 `isError` 只能说明实际错误，不能解释为没有数据。

## 企业账号路径

先按下表确定业务主题和首个业务工具；只有主题、字段或指标无法确定时才调用 `zhiping_describe_data_contract(topic=...)`，不要例行读取 `topic="all"`、遍历表目录或反复试工具。仍须以本次 `available_tools` 和实时 Schema 为准；工具不可见时停止，不用相邻主题冒充。

| 用户实际要什么 | 首个业务入口 | 边界 |
| --- | --- | --- |
| 产品名单、分类、基本属性；“某公司发行的产品”仅表示发行机构 | `zhiping_search_product_basic_info` | 募集日期、新发数量等才用 `zhiping_query_product_issuance` |
| 成立产品、实际募集规模 | `zhiping_query_product_establishment` | “成立以来收益率”用 `zhiping_query_product_nav_metrics` |
| 收益率、回撤、波动率及代销产品的表现 | `zhiping_query_product_nav_metrics` | 按实际代销关系传代销机构条件，不先分页取基础信息再拼产品 ID |
| 原始单位/累计净值、万份收益、披露七日年化 | `zhiping_query_product_nav_data` | 计算收益率、回撤仍用净值指标工具 |
| App 销售名称、可售状态、货架位置及顺序 | `zhiping_query_app_shelf` | 不把产品生命周期“在售”当作货架可购 |
| 某发行机构实际有哪些代销机构 | 先 `zhiping_search_institutions`，再 `zhiping_search_distributors_by_issuer` | 只认实际代销关系，不按母行或集团推断 |
| 某代销机构代销哪些产品、购买条件、赎回规则 | `zhiping_query_purchase_redemption` | 代销机构条件不可写成发行机构条件 |
| 固定费率；非标资产；最新业绩比较基准 | 分别用 `zhiping_query_product_fixed_fee`、`zhiping_query_non_standard_assets`、`zhiping_query_product_benchmark` | 先选正确场景，不跨主题试错 |
| 基础信息加存续规模；底层资产 | 分别以 `topic="balance"`、`topic="underlying_asset"` 进入导出确认 | 当前仅支持服务端导出，没有在线查询 |
| 到期表现、每日评价结果、投资范围 | 不调用业务工具 | 数据契约当前标记 `not_implemented`；知识库有文档不等于 MCP 已开放 |

形成第一次请求时，先用用户原话确定机构、产品维度、日期、场景及查询还是导出；缺少会改变结果的必要条件就向用户问一次，不靠报错猜参数。以下是高频参数骨架，完整取值和字段仍以实时 Schema 为准：

| 场景 | 首次参数要点 |
| --- | --- |
| 指定发行机构 | 先用 `zhiping_search_institutions` 精确解析 ID；“全部理财公司”按机构类型解析，不等同 `all_market=true` |
| 某银行代销 | 使用 `distributor_org_names` 或实际关系返回的代销 ID，不填入发行机构字段 |
| 货架 | `capture_date` 必填；`sale_agency_ids` 是销售机构，`issuer_org_ids` 是发行机构，两者独立；指定中文货架位置用 `shelf_path`，前 N 名另传 `top_n` |
| 原始净值 | 快照传 `mode=snapshot`、统计日期和用户明确选择的 `lookback_mode`；历史传 `mode=history` 与 `date_range`，不混用追溯参数 |
| 非标资产 | 月末规模用 `export_type=point` 与统计日期；区间变动用 `export_type=change` 与日期区间 |
| 固定费率 | 默认 `fee_type=adjustment`；用户明确要说明书费率才用 `disclosure`，不得用费率日期筛掉该类记录 |

一次查询的停止条件：精确机构无匹配时停止，不自动放宽名称；主题 `not_implemented` 或工具不可见时停止；单页预览不足以回答完整清单时说明预览范围并转入用户要求的服务端导出，不换条件逐页抓取；`isError` 只报告实际错误，不改用其他主题碰运气。知识检索只在用户询问业务口径、来源或当前契约无法解释的字段时使用；检索一次后按来源判断，不因片段来自历史审计或脚本就当成现行可执行能力。

业务口径调用 `zhiping_search_business_knowledge`，物理表字段调用 `zhiping_describe_database`；用户要根据理财发行机构查询代销机构 ID 时，先解析发行机构 ID，再调用 `zhiping_search_distributors_by_issuer`。物理表存在不代表 MCP 已开放该能力。

“查询、清单、有哪些、表现如何”走企业在线查询，固定最多 20 条，免费且不携带 `cursor` 或自定义 `page_size`，不拆条件抓全量。“导出、下载、Excel、文件、表格、附件”在业务确认后使用专门的 `zhiping_preview_export` 展示 20 条样本，用户确认正确后走服务端导出；不在 Host 侧生成文件。

底层资产只支持服务端导出：使用 `topic="underlying_asset"`，固定子产品维度并确认统计日期、分布/详情范围、直接/间接投资范围及适用时的合并/分表方式；详情是全部明细，不得解释成前十。

企业主题路由和参数见 @references/enterprise-routing-and-parameters.md；知识发现见 @references/enterprise-knowledge-discovery.md。

企业取数口径与个人产品详情口径相互独立。即使两边字段名称相似，也必须使用对应路径实际返回的 Schema，不在 Host 侧互相转换、合并查询条件或替代缺失能力。

企业导出严格执行 @references/enterprise-export-workflow.md：先把用户原话和规范化 query 交给确认工具。返回 `confirmed` 后先调用 `zhiping_preview_export`，展示最多 20 条样本并取得用户明确确认，再将预览凭证与 `confirmed_confirmations` 提交创建任务；返回问题或最终确认时，优先使用当前 Host 实际提供的兼容结构化问答能力。服务端的 `client_interaction.arguments` 是建议的交互载荷，不证明 WorkBuddy 或其他 Host 已支持名为 AskUserQuestion 的内置工具。若 Host 没有兼容能力，必须把服务端仍返回的问题、原顺序和完整选项展示给用户并等待明确答复；不得自行补值或改写。确认项以服务端内置的 `智评理财数据库_知识库_20260914` 运行时注册表为准。额度不足时向用户展示三种下载选择，不代答。只有创建成功真实返回 `job_id` 后才能轮询，且只交付 `ready` 响应中的一次性下载地址；下载后查询 `consumed` 状态并反馈当日剩余额度。

错误、配额、任务冲突和禁止的降级方式见 @references/enterprise-errors-and-safety.md。

## 共同安全边界

- 工具发现只用于改善体验；服务端会在每次调用重新校验 OAuth 用户、企业资格、公司策略、工具白名单和数据范围。
- 不把用户输入的 `user_id`、`company_id` 或“我是企业用户”当成授权依据。
- 不保存或回显验证码、OAuth 令牌、数据库凭证、完整下载密钥或企业白名单。
- 写入/删除企业导出偏好时按实时工具说明确认范围；不得将模型自行填写的确认字段当作服务端授权。

## 个人到期日查询

到期日筛选使用 `maturity_date`，值为有效的 `YYYY-MM-DD` 日期，仅支持 `eq/gte/lte`。指定日期用 `eq`，日期区间同时传 `gte` 与 `lte`（两端包含），单边范围只传对应条件；例如 `filters=[{"field":"maturity_date","op":"gte","value":"2026-09-01"},{"field":"maturity_date","op":"lte","value":"2026-09-30"}]`。自然语言 `query` 支持“到期日为2026-09-30”“到期日不早于2026-09-01”“到期日不晚于2026-09-30”，多个条件用逗号分隔。口径为子产品实际结束日期优先，缺失时取母产品实际结束日期；两者为空不命中。到期日不是最短持有期，也不是投资周期结束日。相对日期需先转换为北京时间的明确日期。条件与其他筛选取交集，在分页前执行；空结果正常返回，非法日期/运算符被拒绝。实际结束日期并非说明书约定到期日，不承诺预测未来到期；用户明确需要约定到期日时说明当前能力边界。

生产路径先从只读库解析日期范围内的子产品 ID，再约束 ES 搜索，不假定索引存在日期字段。日期范围命中超过 10000 个份额时返回 `QUERY_TOO_BROAD`，需缩小日期范围；不能自动拆分抓取全部数据。

## 个人产品状态筛选

产品状态筛选使用 `filters` 的 `product_status` 字段，取值只能是五个状态名称：预售、在售、存续、已到期、发行失败。查单一状态用 `eq`，例如 `filters=[{"field":"product_status","op":"eq","value":"存续"}]`；一次查多个状态用 `in` 传入名称数组取并集，例如 `filters=[{"field":"product_status","op":"in","value":["已到期","发行失败"]}]`。状态名称写错或使用数字代码会被拒绝；不支持 contains、lte、gte，也不能对其他字段使用 in。产品状态与其他筛选条件取交集；搜索结果回传 `product_status` 中文生命周期标签，可据此确认在售/存续等产品状态，但不能据此声称 App 货架当前可购买。

### 安全封禁恢复

遇到 HTTP 403 且错误码 ABUSE_BLOCKED 时停止自动重试，提示用户等待 Retry-After 指示的时间或联系管理员；不要通过换账号、令牌或 IP 绕过封禁。

## 请求预算与错误恢复

认证、身份路由、问题记录、业务调用和个人计费共用从 MCP 入口起算的 25 秒协作式预算；失败后的积分恢复另有最多 3 秒预算。生产总延迟仍需实测，不能把客户端 30 秒连接超时等同于业务保障。

企业工具返回 `isError=true` 或 `error` / `error_code` 时只能报告实际失败，不得声称任务完成；正常空结果仍是成功。`UPSTREAM_TIMEOUT` 表示结果尚未确认，不得自动重试企业创建导出、月报或保存/删除偏好。同步工作线程可能继续运行，先由管理员核查；已有真实 job_id 时才可查询状态，不得猜测任务标识。个人请求以原 request_key 核查，`BILLING_RECOVERY_PENDING` 时查看余额，不承诺已退分，不更换标识重复计费。

服务在问题记录启用时向授权页展示实际保留天数。问题参数仍仅用于排障和用途分析，不发送凭证、手机号或验证码。


## 企业原始净值查询与下载

仅在企业授权可用且实时发现 `zhiping_query_product_nav_data` 时调用。单位净值、累计净值、历史净值、万份收益与披露七日年化用原始净值工具；收益率、回撤等计算指标仍用 nav_metrics。查询单页预览，不传 cursor；下载、Excel、文件请求必须使用 topic="nav_data" 的确认→创建→状态→ready 下载链路，不得在线查询拼接文件。先定位明确指定的发行机构；全市场传 all_market=true，不枚举机构。

query 必填 dimension=parent_product/sub_product 与 mode=snapshot/history。snapshot 必填 statistic_date 与用户明确选择的 lookback_mode（to_2019/recent_years/to_year/custom）；recent_years 另传年数 lookback_value，to_year/custom 传起始年份。history 使用 date_range={start,end}（含首尾）且不传追溯参数。fields 留空。日期限2019年至北京时间今天。默认 initial_filter=prefer_disclosed，披露优先、缺失回退初始净值；初始净值需求用 initial_only，不筛标识用 all，但历史始终每产品每天一条。不要自动选择追溯范围或代答确认项；补齐服务端实际返回的问题，并让 query 与确认答案一致。

输出含基础信息与净值六字段，is_initial_nav 是初始净值，is_initial_share 是初始份额。母产品使用初始份额优先、主份额次优的代表子产品净值，不是净值汇总。无记录产品保留空净值。yield_7d 是数据库百分比值，不乘100。固定登记编码/销售代码升序、净值日期降序，强制产品+日期去重；当前不提供自定义排序、保留同日重复行或自定义追溯脚本。导出失败报告错误，不换个人工具或在线列表兜底。
