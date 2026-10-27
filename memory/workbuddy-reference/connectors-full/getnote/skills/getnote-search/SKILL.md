---
name: getnote-search
description: 用户通过得到大脑 MCP 按录音台、录音卡来源，或书名、作者、课程、老师、文稿、评论查找笔记、划线、完整列表和原文时使用。
description_zh: 按录音台、录音卡来源找笔记；通过得到大脑 MCP 按书名、作者、课程、老师、文稿或评论查找笔记和划线。
description_en: Use when finding notes from a recording station or recorder card, or finding GetNote notes or highlights by book, author, course, teacher, article, or personal comment, or retrieving complete lists and originals through MCP.
version: "1.1.2"
author: GetNote
---

# 得到大脑查找笔记

## 录音台与录音卡来源定位

用户说“录音台”“AI录音台”“墨案/MOAN录音台”时，调用 `list_notes` 并设置 `source=vendor_moan`；“录音卡”“GetSeed录音卡”用 `source=getseed`。设备来源与note_type独立，查设备全部笔记只限定来源，不额外加audio/meeting；getseed同时涵盖长录音和闪念，用户明确指定模式才加recorder_audio或recorder_flash_audio。普通录音未限定设备时不加设备source。

```json
{"name":"list_notes","arguments":{"source":"vendor_moan","page_size":20}}
```

“昨天/上周”叠加用户时区换算的created_from/created_to，RFC3339、from包含、to不包含；修改时间用对应edited/record_updated字段。翻页始终保留source、时间和知识库等全部原条件，核对applied_filters.source与notes[].source，按pagination.next_cursor继续、按字符串note_id去重。has_more=false且无未解决partial/warnings/错误时才完成当前授权范围；旧total不是设备范围总条数。空结果不换来源补数，不用标签或标题猜设备。

已明确的普通录音模式可用note_type精确筛选：audio=即时录音，local_audio=上传音频，class_audio=课堂录音，meeting=会议，internal_record=内录，recorder_audio/recorder_flash_audio=录音卡长录音/闪念。只说“录音”且模式不明时保留多类型候选并说明范围，不把其中一种当作全部录音；这些是已知类型，不把未来服务端有效类型封死。

转写调用示例：

```json
{"name":"get_note_transcript","arguments":{"id":"<note_id>"}}
```

只覆盖已同步成笔记且当前账号有权读取的主笔记，不代表设备端未同步、尚未形成笔记的全部会议记录。设备范围内有正文主题时先按来源列候选，再按需读取正文核对；recall没有设备source筛选，不能将全局Top-K后过滤当作完整设备搜索。转写调用get_note_transcript，不把总结或preview当原文；转写缺失不等于笔记不存在。

## 已知得到课程的执行闭环

1. 按用户已给的课程名/准确老师定位。exact 返回唯一课程且目录已结束时，固定该 `course_type/course_id`，进入课程笔记列表。只有名称不完整、未命中、多个候选或目标相关的映射缺失时，才按用户已有线索补定位。
2. 保持课程身份、用户时间与知识库约束，遍历 `note_type=ref` 到 `has_more=false`。只有目标课程不存在未解决的映射缺失、目标相关 partial/warnings 或读取错误时，才完成当前同步、授权范围内的课程引用遍历。存在这些问题时交付已有结果并明确未完成，只补目标项；无需为此补查其它课程。无关课程的目录警告不改变已定位课程的结果。
3. 少量匹配笔记用 `get_notes` 按需读正文；用户同时要原文和个人评论时，`fields` 同时选 `original` 与 `content`。逐项成功、无 pending 后，按“引用原文／个人评论／已同步范围”交付；有失败项则保留未完成状态并只补目标项。

唯一课程已定位后，不再为同一目标扩查该老师的其它课程或换 prefix 重复定位。课程引用遍历完成后，不取消课程/类型条件去全账号交叉核对；这种查询不能证明用户另写的心得或课堂录音是否存在，也不能据此说其它类型已查全。

## 先选择路径

课程划线是 `note_type=ref` 的引用笔记；`get_note_marks` 读取录音、会议等笔记里的标记。课程或书籍划线用来源列表及 `get_notes` / `get_note_original` 完成，标记接口不参与该引用集合的完整性核对。用户明确说录音里的标记时，再按笔记 Skill 查询标记。

| 用户说法 | 工具与范围 |
|---|---|
| “找《沙之书》的笔记” | 读知识库 Skill；`list_topics` 传 `scope=BOOKSPACE, query=沙之书`。 |
| “找博尔赫斯写的书的笔记” | `list_topics` 传 `scope=BOOKSPACE, author=博尔赫斯`，省略 query。query 只匹配书名；两者同时传是 AND。 |
| “找提到博尔赫斯的笔记” | 人名在这里是内容主题，使用 `recall`，不能限制为其著作。 |
| “这本书的全部笔记／划线” | 定位真实 topic_id 后用 `list_topic_notes` 翻页；全部笔记不加 note_type，只有划线才加 `note_type=ref`。 |
| “万维钢精英日课那篇文稿的划线” | 按下面的课程路径，核对课程、老师、季数和文稿，不能只搜一次就判定不存在。 |
| “某课程共读某本书那篇” | 保持课程文稿范围，书名是文稿线索，不跳到书籍库。 |
| “某库里关于某主题” | 先确认 topic_id，再 `recall_knowledge`；空结果在同一库用 `list_topic_notes` 补查。 |
| “以前关于某主题记过什么” | `recall`；指定知识库则 `recall_knowledge`。 |
| “最近有哪些笔记” | `list_notes`；浏览列表不是语义搜索。 |

`recall` 使用 query、可选 top_k；`recall_knowledge` 另需真实 topic_id。Top-K 不是完整清单。用户只说课堂笔记时不能擅自限定得到课程，应区分课堂录音与课程引用。

## 得到课程、文稿与评论

先保留课程名、季数、老师、文稿、主题、时间和知识库范围。已知得到课程名或老师时先用 `list_reference_resources`，无需知识库，也不要求用户给内部 ID：

```json
{"name":"list_reference_resources","arguments":{"source":"dedao","resource_type":"course","query":"科学课","author":"老师准确姓名","match_mode":"exact","page_size":20}}
```

query只匹配课程名，author精确匹配老师，二者AND且至少一个；只传已知线索，简称不冒充老师准确姓名。课程名不完整可显式prefix，正文主题不塞进课程名。核对版本和老师，同名课程先消歧。使用返回的字符串course_type+course_id，当前66，不从标题、URL或课节ID生成：

```json
{"name":"list_notes","arguments":{"source":"dedao","note_type":"ref","reference_course_type":"<course_type>","reference_course_id":"<course_id>","page_size":20}}
```

课程筛选覆盖课程直接引用和所属课节；额外加reference_resource_type=course_article会再限为课节，reference_resource_id仍是原课节ID，组合取交集。保留用户时间条件；首版course+topic_id不支持，不能去掉真实知识库后查个人笔记交差。课程内语义搜索尚未开放，不能在全局Top-K后过滤冒充；精确列表后按需读正文核实主题。

先确认tools/list存在新工具，并核对接口meta.capabilities中的reference.resources.v1/note.course_filter.v1及applied_filters；旧服务不支持就说明能力尚未发布，不删除课程条件继续。旧工具和默认请求保留。目录页可能resources=[]且has_more=true，保持条件用真实next_cursor继续；循环或错误表示未完成。用户要全部时遍历到has_more=false，按字符串note_id去重，只能称当前同步、授权范围已遍历，不等于得到App全部划线。

已知单篇文稿ID/URL时沿用既有list_notes精确筛选source+reference_resource_type+reference_resource_id，或reference_article_url；课节ID不是课程ID，课程主页不传文章URL。只有文稿标题可先recall查候选并核对reference.article.title，得到课程线索后用上述课程流程。只有课程主页URL时核对真实reference.url，不解码猜ID；旧来源定位预算5页用完要说明未完成，不凭缺失/partial元数据排除候选。topics=[]不阻断来源路径。

已定位少量个人笔记时用 `get_notes` 单次读取：

```json
{"name":"get_notes","arguments":{"ids":["<note_id_1>","<note_id_2>"],"fields":["original"]}}
```

最多20个去重ID，original显式选择，引用原文来自ref_content；查个人评论选fields=content。逐条核对status/content_state，保留pending_ids/partial。deferred_budget整条待读，不是截断成功；用户确需全文时针对该ID用get_note_original的id参数补读，不自动并发20次get_note。知识库共享详情继续旧单条权限路径。原文、评论、preview和snippet不混用，不按文章ID合并不同笔记ID的划线。

只记得评论时先语义搜索实质文字并读content核实；已限定课程先缩小该课程候选，不因preview没出现评论而排除。多于20个候选先保留课程/文稿/时间缩小，不下载整个账号全文。未限定来源时不能只查课程便声称全部评论查完。

语义定位可显式recall/recall_knowledge传response_mode=snippet、max_snippet_chars=100..1000（默认400），需search.snippet.v1。不传保持旧正文/top_k。full只表示完整命中片段，不是完整笔记；保留snippet_source、truncated、content_state和未知未来资源类型。

## 完整性与结果呈现

- 列表核对 meta.capabilities、applied_filters 和 pagination；工具拒绝条件时不得去掉条件后交差。所有条件在翻页、从语义候选转精确来源时继续保留。时间用 created/edited/record_updated 的 from/to 对应字段、RFC3339 时区，不把时间词拼进 query 当作实际筛选。
- 新搜索结果使用 note.display_title 展示、snippet 解释命中；旧结构可用 title/content。真正笔记类型是 note.note_type，顶层旧 note_type 可能表示 NOTE/FILE 资源类型。无 note_id 的文件结果不能拼成笔记链接。
- 同一笔记多个命中片段按字符串 note_id 去重；不同笔记 ID 的划线不能合并。returned_count 是片段数，distinct_note_count 是笔记数，score=null 是未知。summary、preview、snippet 都不能代替全文。
- 书籍查找先核对 BOOKSPACE 和真实 book_id，普通知识库同名不等于同一本书。reference.author 与 book_info.author 内容不同须分别列出；source 的契约大小写差异、reference.location.page=0 未知页码本身不作为作者冲突或数据错误的证据。
- partial=true 或 warnings 表示结果不完整；超时、权限失败不等于没有。只说“本次范围未找到”，不能根据空 Top-K 断言全库没有。
- 得到课程引用列表只覆盖课程划线，用户另写的课程心得／课堂录音需按实际类型与语义线索补查，不能称全部课程笔记都覆盖了。
- 只展示真实标题、必要摘要、字符串 ID 和返回链接，不编造访问 URL。搜索不自动创建、修改、移动或分享笔记；群聊不主动展开私密全文。错误保留reason、retryable、request_id、field、supported_values和retry_after_seconds。Gateway已对明确只读瞬时429做最多额外2次、累计等待5秒重试；Agent不叠加无界重试，配额、写操作和凭证签发不自动重试。
