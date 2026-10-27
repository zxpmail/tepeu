---
name: coros-skill
description: 查询与分析 COROS 运动和健康数据，创建与修改训练计划、课程，并安排训练日程
version: "1.1.0"
author: "COROS"
---

# COROS Skill

本 Skill 指导 AI 通过 COROS Connector 查询与分析用户的运动和健康数据，以及创建、修改训练计划和课程、安排训练日程。数据来自用户授权绑定的 COROS 账号。查询不会改动训练内容；创建、修改和排期会保存到该账号，仅在用户明确要求时执行，不要把咨询或方案讨论当作保存授权。

## 关键约定（调用前必读）

1. **日期格式**：所有日期参数使用 `yyyyMMdd`（如 `20260824`）。日期窗口按用户 COROS 账号档案中的时区计算，由服务端自动处理，**不要**尝试传入时区。若服务端按 UTC 兜底计算，结果末尾会附带披露说明，请一并转述给用户。
2. **睡眠归属日**：每晚睡眠记在**醒来那天**。"昨晚睡得怎么样"应传**今天**的日期；"前晚"传昨天的日期。用户明确指定某个日期时按原样传入（与 COROS App 当天展示一致）。该规则适用于 `querySleepData`、`querySleepHrv` 以及 `queryDailyHealthData` 的睡眠部分。
3. **活动查询链路**：先用 `querySportRecords` 获取活动的 `labelId`、`sportType`（及起止时间戳），再调用详情/分段/分析/FIT 工具。`labelId` 与 `sportType` 必须来自列表结果，不要臆造。
4. **运动类型传数字代码**：`sportTypeCodes` 只传 COROS 数字代码，不要传名称。完整代码表见 `querySportRecords` 工具下方；按用户的实际意图选择对应代码组，不要机械合并相邻品类（例如用户说"跑步"传 [100,101,102,103]，不要顺带把徒步 104、登山 105 也传上），代码表以外的值不要猜测。
5. **心率问题选对工具**：用户只问心率、平均心率、静息心率或心率趋势时，用 `queryAvgHeartRate` / `queryRestingHeartRate`；`queryDailyHealthData` 是整体每日健康总览，不要用它单查心率。
6. **HRV 结论以官方评估为准**：`querySleepHrv` 同时返回官方日评估与原始时序，日均值和正常范围直接使用官方评估段，不要自行从原始点计算。
7. **参数必填标注**：下方参数表的"必填"列有三种取值——`✓` 必须传有效值；`✓*` 接口 schema 标为必填、但语义上可不指定，**调用时参数仍要携带**，用下述占位值表达"不指定"：日期/字符串传空字符串 `""`，天数/条数直接传默认值（如 7、20），距离/时长筛选传 `0`（服务端把 ≤0 视为不筛选），`sportTypeCodes` 不筛选时传 `[65535]`；`-` 为可省略参数。
8. **返回形式**：除 `downloadActivityFitFiles` 返回 FIT 二进制资源外，其余工具返回面向用户的格式化文本，可直接引用或总结到回答中。
9. **训练对象必须区分**：训练计划、课程库模板、某天的独立日程课程是不同对象；按下方训练工具说明分流。修改前先查询对应详情，不要通过新建替代修改。编号必须来自工具结果，完整保留并按工具参数要求传递，不要向用户展示内部编号。
10. **以当前工具定义为准**：调用前读取当前连接提供的工具说明与参数结构，尤其是训练课程的嵌套参数、强度规则和可编辑条件；不要凭本说明猜字段。若当前连接未提供某个工具，如实说明当前连接暂不可用，不要声称已保存。

## 可用工具

### 用户与设备

#### queryUserInfo - 查询用户基础信息

查询用户档案：身高、体重、生日（含年龄）、性别、昵称。无参数。

**使用示例**：用户问自己的身高体重、年龄或个人档案时调用。

#### queryDevices - 查询绑定设备

查询用户绑定的 COROS 设备列表，含设备标识、固件类型和自定义名称。无参数。

**使用示例**：用户问"我绑了哪些设备 / 我的手表"时调用。

### 运动记录与活动

#### querySportRecords - 查询运动记录（活动列表入口）

按条件筛选用户的运动记录。这是所有活动详情类工具的入口：后续工具所需的 `labelId`、`sportType`、起止时间戳都来自本工具的返回结果。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd；传 "" 时窗口为包含 endDate 在内的 7 天（两端都传 "" 即最近 7 天，含今天） |
| endDate | string | ✓* | 结束日期 yyyyMMdd；传 "" 时默认今天 |
| sportTypeCodes | integer[] | ✓* | COROS 数字运动类型代码，见下方代码表；不筛选传 [65535] |
| minDistanceKm | number | ✓* | 最小距离（公里）；不筛选传 0 |
| maxDistanceKm | number | ✓* | 最大距离（公里）；不筛选传 0 |
| minDurationMinutes | integer | ✓* | 最短时长（分钟）；不筛选传 0 |
| maxDurationMinutes | integer | ✓* | 最长时长（分钟）；不筛选传 0 |
| maxAveragePace | string | ✓* | 最大平均配速，如 "5:30"；不筛选传 "" |
| locationKeyword | string | ✓* | 地点关键词，如城市名或公园名；不筛选传 "" |
| limit | integer | ✓* | 返回条数上限，默认 20 |

**返回**：日期、运动类型、地点、时长、距离/组数、平均配速或速度、labelId、sportType，以及活动的 startTimestamp/endTimestamp（如有）。

**COROS 运动类型代码表**（sportTypeCodes 只能从中选取）：

| 品类 | 代码 |
|------|------|
| 跑步/徒步 | 100 outdoor run, 101 indoor run, 102 trail run, 103 track run, 104 hike, 105 mountain climb, 106 multi-pitch sub climb |
| 骑行 | 200 outdoor bike, 201 indoor bike, 202 e-bike, 203 gravel bike, 204 mountain bike, 205 mountain e-bike, 299 helmet bike |
| 游泳 | 300 pool swim, 301 open water swim |
| 有氧/力量 | 400 gym cardio, 401 GPS cardio, 402 strength |
| 滑雪 | 500 ski, 501 snowboard, 502 XC ski, 503 alpine touring |
| 飞行 | 600 fighter |
| 划船/水上/钓鱼 | 700 rowing, 701 indoor row, 702 whitewater, 704 flatwater, 705 windsurfing, 706 speedsurfing, 707 boat fishing lure, 708 shore fishing lure, 709 pond fishing lure, 710 kayak fishing lure, 711 inshore fishing, 712 offshore fishing, 713 boat fly fishing, 714 shore fly fishing, 715 surf fishing |
| 攀岩 | 800 indoor single pitch, 801 bouldering, 802 outdoor climb |
| 健走/健身 | 900 walk, 901 jump rope, 902 stair climbing, 903 elliptical, 904 yoga, 905 pilates, 906 boxing |
| 球类 | 1000 badminton, 1001 ping pong, 1002 basketball, 1003 soccer, 1004 pickleball, 1005 tennis, 1006 padel |
| 休闲 | 1100 frisbee, 1101 skateboard |
| 综合体能 | 1200 hybrid fitness |
| 户外自定义 | 9800-9807（球类/休闲/山地/高海拔/机动车/水上/探险/其他） |
| 室内自定义 | 9900-9904（球类/力量/塑形/舞蹈/其他） |
| 通用自定义 | 9999 |
| 多项运动 | 10000 triathlon, 10001 free combine, 10002 climb ski, 10003 multi-pitch climb |
| 跟随路线 | 25301 |
| 全部 | 65535 |

**使用示例**：
- 最近一次跑步：sportTypeCodes 传 [100,101,102,103]，limit 传 1
- 上周所有训练：传上周日期范围，sportTypeCodes 传 [65535]

#### getActivityDetail - 查询活动详情

获取单次训练活动的详细数据：心率、配速/速度、海拔、踏频等。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| labelId | string | ✓ | 活动 labelId，来自 querySportRecords |
| sportType | integer | ✓ | 运动类型代码，如 100=跑步、200=骑行 |

#### analyzeActivityDetail - 教练式活动分析

先获取活动详情，再用通俗语言给出教练风格的训练总结，可指定分析侧重点。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| labelId | string | ✓ | 活动 labelId，来自 querySportRecords |
| sportType | integer | ✓ | 运动类型代码 |
| focus | string | ✓* | 分析侧重点，如配速稳定性、速度、心率；无侧重传 "" |

#### queryActivityLapData - 查询活动分段数据

查询指定活动的分段（lap/segment）数据，仅返回 COROS App 对该运动类型展示的字段。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| labelId | string | ✓ | 活动 labelId，来自 querySportRecords |
| sportType | integer | ✓ | 运动类型代码，如 100=跑步、1200=综合体能 |

#### queryCustomActivityLapData - 查询自选时间窗分段数据

对单次活动中用户指定的精确时间窗做分段统计。适用于"最后 N 分钟""最后一段""某个时间区间"类请求；默认分段请用 queryActivityLapData。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| labelId | string | ✓ | 活动 labelId，来自 querySportRecords |
| sportType | integer | ✓ | 运动类型代码 |
| startTimestamp | integer | ✓ | 选取窗口起点 Unix 秒；"最后 N 分钟"传活动 endTimestamp - N × 60 |
| endTimestamp | integer | ✓ | 选取窗口终点 Unix 秒；"最后 N 分钟"传活动 endTimestamp |

两个时间戳应落在活动时间窗内。

#### downloadActivityFitFiles - 下载活动 FIT 文件（二进制）

以二进制资源形式返回一个或多个活动的原始 FIT 文件，用于深度数据分析。客户端能解析 FIT 时使用解析出的全部数据，不要假设固定字段；无法原生解析时可用 Python（如 fitparse）解码。

**参数说明**（本工具参数在 schema 中均为可选）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| labelId | string | - | 指定单个活动；传了则必须同时传 sportType |
| sportType | integer | - | labelId 存在时必填；日期模式下可作为可选的运动类型过滤 |
| startDate | string | - | 未传 labelId 时按日期范围批量下载，yyyyMMdd；全部省略时默认今天 |
| endDate | string | - | 默认等于 startDate；不得早于 startDate，倒序会报错 |
| limit | integer | - | 返回文件数上限，默认 5，最大 10 |

查询过去某一天的文件时务必传 startDate（endDate 可传同一天或省略）；**不要只传过去的 endDate**——startDate 会默认今天，构成倒序而报错。

**额度**：与 queryActivityFitFileDownloadUrls 共用同一个固定 24 小时计数窗口（后续调用不会延长该窗口），窗口内最多 50 个文件。每个成功返回的 FIT 文件消耗 1 个额度；无匹配结果或调用失败不消耗。

#### queryActivityFitFileDownloadUrls - 查询 FIT 文件下载地址

以文本形式返回活动原始 FIT 文件的下载 URL。仅当客户端无法接收 downloadActivityFitFiles 的二进制资源时作为兜底；参数与 downloadActivityFitFiles 完全一致（含"查过去某天传 startDate"的规则），每个成功返回的 URL 消耗同一个 24 小时窗口 50 个文件的额度。

### 每日健康与睡眠

#### queryDailyHealthData - 查询每日健康总览

返回每日综合健康数据：步数、卡路里、压力、睡眠质量与时长，以及作为总览一部分的心率摘要。只问心率时不要用本工具（见"关键约定"第 5 条）。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| days | integer | - | 最近查询天数，默认 7 |

#### querySleepData - 查询睡眠数据

返回睡眠评分、主睡眠时长、深睡/浅睡/REM 比例、清醒时长与次数、主睡眠窗口和小睡窗口。日期为**醒来日**（见"关键约定"第 2 条）。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd（醒来日）；无明确日期传 "" |
| endDate | string | ✓* | 结束日期 yyyyMMdd（醒来日）；无明确日期传 ""；只传任一端日期时按该日单日查询 |
| days | integer | ✓* | 未给日期时的最近天数，默认 7 |

### 健康统计

#### queryAvgHeartRate - 查询每日平均心率

返回区间内每天的平均心率。显式 yyyyMMdd 日期优先于最近 N 天查询。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | - | 开始日期 yyyyMMdd |
| endDate | string | - | 结束日期 yyyyMMdd，省略时等于 startDate |
| days | integer | - | 未传日期时的最近天数，默认 7 |

#### queryRestingHeartRate - 查询每日静息心率

返回区间内每天的静息心率。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| days | integer | - | 最近查询天数，默认 7 |

#### querySleepHrv - 查询睡眠 HRV

返回官方睡眠 HRV 日评估（平均 HRV、正常范围、基线、评价）及睡眠期间原始 HRV 时序。问睡眠 HRV、恢复状态、某天 HRV 为何偏低时用本工具。日期为醒来日："昨晚"传今天的日期。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd（醒来日）；无明确日期传 "" |
| endDate | string | ✓* | 结束日期 yyyyMMdd；传 "" 时等于 startDate |
| days | integer | ✓* | 未给日期时的最近天数，默认 7，最大 7（超出按 7 处理） |

#### queryStressLevel - 查询每日平均压力

返回区间内每天的平均压力值。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| days | integer | - | 最近查询天数，默认 7 |

#### queryStressTimeSeries - 查询压力原始时序

返回压力时序点（时间戳、时区、压力值、展示分、压力 HRV、压力心率）。适合"今天压力怎么变化的"类问题。**无论显式日期还是最近 N 天，窗口都不得超过 7 天**，显式范围超 7 天会报错。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd；无明确日期传 "" |
| endDate | string | ✓* | 结束日期 yyyyMMdd；传 "" 时等于 startDate |
| days | integer | ✓* | 未给日期时的最近天数，默认 1，最大 7 |

#### queryHealthCheckTimeSeries - 查询健康快测时序

返回最近一次完整健康快测（wellness check）的原始心率、HRV、压力、呼吸率和血氧时序；日期范围与历史兜底规则见下文。

**历史兜底规则**：未指定日期时先查最近 N 天；最近窗口内没有完整快测，会自动兜底返回**历史上最近一次**完整快测（可能远早于查询窗口）。结果中会标注这次快测的实际日期——回答时必须按该实际日期表述，不要把历史记录说成近几天的数据。指定了明确日期时只查该日期范围，不做历史兜底。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd；无明确日期传 "" |
| endDate | string | ✓* | 结束日期 yyyyMMdd；传 "" 时等于 startDate |
| days | integer | ✓* | 未给日期时的最近搜索天数，默认 7，最大 7（超出按 7 处理） |

### 体能与恢复

#### queryFitnessAssessmentOverview - 查询体能评估概览

返回 VO2max、跑力等级、阈值配速和 5 公里/10 公里/半马/全马成绩预测。无参数。

#### queryTrainingLoadAssessment - 查询训练负荷评估

返回近日训练评语、短期负荷、长期负荷和负荷比，用于判断近期训练量是否合适。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| days | integer | - | 最近查询天数，默认 7 |

#### queryRecoveryStatus - 查询恢复状态

返回当前体力恢复百分比、恢复等级和预计完全恢复时间。无参数。

### 训练日程

#### queryTrainingSchedule - 查询训练日程

返回用户的 COROS 训练安排。适合"我这周/明天/某段时间该练什么"类问题。

**注意**：

- 返回结果可能包含内部 Plan ID 和 idInPlan 字段，**不要向用户展示这些内部 ID**。
- 查询“我的训练计划”用 `queryTrainingPlanLibrary`；查询某天练什么或定位某天要修改的课程用本工具。
- 日程中的计划内课程通过 `queryTrainingPlanDetails` → `updateTrainingPlan` 修改；独立课程通过 `queryScheduledWorkoutDetails` → `updateScheduledWorkout` 修改。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDate | string | ✓* | 开始日期 yyyyMMdd；无明确日期传 "" |
| endDate | string | ✓* | 结束日期 yyyyMMdd；无明确日期传 "" |

**日期默认规则**：两个参数都传 "" 时查询本周（周一到周日）；只给 startDate 时查询该单日；只给 endDate 时窗口为今天到该日期。

### 训练计划、课程库与日程课程

以下工具均以当前连接提供的参数结构为准。本节说明如何选择工具及避免改错对象，不重复完整嵌套结构。

| 用户意图 | 工具与调用顺序 | 保存位置与注意事项 |
|------|------|------|
| 查看我的训练计划 | `queryTrainingPlanLibrary` → `queryTrainingPlanDetails` | 列表区分计划状态与是否可编辑，不要把“我的计划”误当作日程查询 |
| 创建多周训练计划 | `queryTrainingPlanLibrary` → `createTrainingPlan` | 先设计训练内容再保存；计划从指定开始日期自动执行。与进行中计划日期重叠时，说明风险并取得确认后再创建 |
| 修改已有训练计划 | `queryTrainingPlanLibrary` → `queryTrainingPlanDetails` → `updateTrainingPlan` | 只能使用进行中、recordRole=execution 且 editable=true 的执行记录；不能用库原件、已完成或不可编辑的记录 |
| 查看课程库 | `queryWorkoutLibrary` → `queryWorkoutDetails` | 查询用户自己的可复用课程模板 |
| 保存一节可复用课程 | `createSingleWorkout` | 只保存到课程库，不安排日期 |
| 修改课程库模板 | `queryWorkoutDetails` → `updateWorkoutDetails` | 先核对可编辑状态，再提交完整课程；不会改变已排到日程的副本 |
| 某天新建一节课程 | `createScheduledWorkout` | 直接保存到该天，不进入课程库；不要先建库课程再排期 |
| 把库里已有的课安排到某天 | `queryWorkoutLibrary` → `scheduleWorkout` | 把模板复制到日程，不改变原件；包括库里已有的力量、游泳等课程 |
| 查看或修改某天的独立课程 | `queryTrainingSchedule` → `queryScheduledWorkoutDetails` → `updateScheduledWorkout` | 用日程返回的日期（作为 date 参数）和 idInPlan 定位；修改只影响日程副本，不回写课程库；只查看时不调用更新 |

**训练计划边界**：

- 新建计划为 4–16 个自然周，开始日期在今天至今天后 14 天内；首周可不足一周，结束日为最后一周周日。最后一周必须有训练课程。
- 更新计划只提交要改的 dayNo；未提交的天不变，提交的某一天会整天替换，必须包含该天要保留的全部课程。休息日条目会清空该天的课程。
- 若某天已有课程完成，该天整体跳过并保留原内容；按返回结果告诉用户哪些天未修改。调整总周数时，需同时提供匹配新长度的完整阶段安排；缩短计划会移除超出新结束日的课程，不能擅自缩短。
- 退出或删除计划、移动或移除独立日程课程仍需使用 COROS App，不要用清空全部课程或另建一节课模拟这些操作。

**课程与排期边界**：

- 新建课程支持跑步、骑行、越野跑；训练课程的 sportType 使用 1/2/5，计划休息日使用 4，不能使用运动记录查询中的 100/200 等代码。力量、游泳等已有库课程可排期，但不能据此承诺可新建或修改这些课程。
- 课程名称和说明使用对话语言、面向运动员的自然表达，不写内部字段名或编号。目标值使用整数米或秒；段级强度、自由模式、不设置强度及各运动支持范围严格遵循当前工具说明。
- `createScheduledWorkout` 与 `scheduleWorkout` 每次处理一节课和一个日期，日期为今天至今天后 90 天（含边界）。同一天已有其他课程不阻止安排，但必须转述返回结果中的其他课程，提醒用户避免重复训练。
- 同一库课程已在同一天排期时，`scheduleWorkout` 不再新增副本；直接新建课程不能假设有此去重保证。
- 更新库课程或独立日程课程前，先查询详情并核对可编辑状态，提交包含保留部分的完整课程；不能只发用户要改的那一段。不可编辑时按返回原因说明，不能绕过限制新建替代品。
- 日程副本与库模板相互独立。修改日程课程后，以返回的新编号为准，不能假设 idInPlan 永久不变。

### 生理周期

#### queryMenstruationCycles - 查询生理周期

返回今日经期状态、下次经期开始日期、每日阶段状态、周期日期区间和用户备注。

**参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| startDay | integer | - | 开始日期 yyyyMMdd 整数；省略或 0 表示今天前 30 天 |
| endDay | integer | - | 结束日期 yyyyMMdd 整数；省略或 0 表示今天后 365 天 |

## 典型工作流

- **分析最近一次跑步**：`querySportRecords`（sportTypeCodes=[100,101,102,103]，limit=1）→ `analyzeActivityDetail`（labelId、sportType、focus 按用户关注点）。
- **上周训练总结**：`querySportRecords`（上周日期范围）→ 必要时对重点活动调 `getActivityDetail` → 配合 `queryTrainingLoadAssessment` 给出负荷结论。
- **最后 10 分钟表现**：`querySportRecords` 拿到活动 endTimestamp → `queryCustomActivityLapData`（startTimestamp=endTimestamp-600，endTimestamp=endTimestamp）。
- **FIT 深度分析**：`downloadActivityFitFiles` 获取二进制并解析；客户端收不了二进制资源时改用 `queryActivityFitFileDownloadUrls`。
- **恢复状态综合判断**：`queryRecoveryStatus` + `querySleepHrv` + `queryRestingHeartRate` 交叉给结论。
- **明天新建一节跑步课**：按用户目标设计课程 → `createScheduledWorkout`；不调用 `createSingleWorkout`。
- **把明天已安排的跑步课改短**：`queryTrainingSchedule` 定位课程 → 若是独立课程，`queryScheduledWorkoutDetails` → `updateScheduledWorkout`；若属于计划，查询可编辑执行计划及详情后用 `updateTrainingPlan`，保留同一天其他课程。
- **把库里已有课程排到周五**：`queryWorkoutLibrary` 定位模板 → `scheduleWorkout`；后续修改周五的课走日程副本，不改库模板。
- **创建多周计划**：结合用户目标和现有训练数据设计内容 → `queryTrainingPlanLibrary` 检查已有计划与日期重叠 → 必要时取得重叠确认 → `createTrainingPlan`。

## 认证与授权

- 连接采用标准 OAuth 2.1 + PKCE，由 WorkBuddy 自动完成：用户点击"连接"后跳转 COROS 账号登录与授权页面，登录并同意授权即可使用，无需手动获取或填写任何 Token。
- access_token 过期时由 WorkBuddy 使用 refresh_token 自动续期，对用户透明。
- 当工具返回"未绑定 COROS 账号""授权已失效""请重新连接"类提示时，说明授权链路需要重建：引导用户在 WorkBuddy 中对 COROS Connector 重新执行连接（重新授权），不要换参数重试。

## 错误与边界

- **未授权 / 绑定失效**：工具会返回面向用户的可读提示，直接转述并引导重新连接。
- **查询无数据**：属正常情况（当天未佩戴设备、未开启对应功能、无该类型记录），如实告知用户，不要编造数据。
- **FIT 下载额度**：两个 FIT 工具共用同一个固定 24 小时计数窗口（后续调用不会延长该窗口），窗口内最多 50 个文件；单次最多 10 个（默认 5）。每个成功返回的文件或 URL 消耗 1 个额度，无匹配结果或调用失败不消耗。额度用尽时工具会返回提示，此时不要重试，告知用户等窗口过期后再试。
- **日期倒序**：常规查询工具会宽容交换起止日期；FIT 两个工具和生理周期工具遇到倒序/不合法窗口会显式报错，需修正后重试。
- **时序窗口上限**："最近 N 天"模式下压力时序、健康快测、睡眠 HRV 都最多 7 天（超出按 7 处理）；显式日期范围只有压力时序强制不超过 7 天（超出报错），需要更长区间时分段查询；睡眠 HRV 和健康快测需要超过 7 天时，直接改用显式日期范围即可（不受 7 天限制）。
- **重试**：查询类工具失败不改变任何状态，可修正参数后重试；FIT 工具失败同样不消耗额度，但每次**成功**调用都会按返回的文件数计入额度——不要对同一活动重复成功下载。
- **写入结果不确定**：创建、修改或排期遇到超时、连接中断或不完整响应时，不能假设未保存并立即重试。先查询计划库、课程库或对应日程核对实际状态；仍不能确认时告知用户核查 COROS App，不要重复写入。仅根据明确的成功结果宣称保存完成。
