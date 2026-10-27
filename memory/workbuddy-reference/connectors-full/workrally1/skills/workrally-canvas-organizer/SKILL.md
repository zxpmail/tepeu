---
name: workrally-canvas-organizer
description: "WorkRally 无限画布整理工具（MCP 版）— 把一个凌乱的画布按素材类型（人物 / 场景 / 视频 / 散图等）自动分组到不同画板(artboard)，网格化排列。支持整理后可选清理「散落的旧媒资节点」（image/video 孤儿），文字节点永远保留不动。全程通过 WorkRally MCP 工具完成，不依赖 workrally CLI。适用于画布素材超过 20+ 节点、想要一键归类的场景。触发词：整理画布、画布整理、画布分类、canvas 归类、organize canvas、canvas cleanup、把画布分画板、按类型分画板、画布重排、canvas tidy up、清理画布散落节点、删除原节点保留文字。不适用：出片流程收尾时的阶段归档画布（由 workrally-brand-film-pipeline 生成）、在画布上生图或生视频、资产库整理（用 material_manage）。"
version: 3.0.0
metadata:
  author: Moke
  requires:
    bins:
      - python3
---

# WorkRally 画布整理 Skill（MCP 版）

> **执行通道**：本 skill 全程使用 **WorkRally MCP 工具**（`mcp__workrally1__*`）。
> **不要**调用 `workrally` CLI，也不要依赖 `WORKRALLY_API_KEY`——鉴权由 MCP 连接器托管。
>
> **平台唯一性**：画布与媒资读写只走 WorkRally MCP，**不得**改用其他 MCP server、第三方 API 或本地/网页版工具旁路操作画布；画布上的素材只能来自 WorkRally，不把外部生成或外部图床的素材写入画布。完整规则见 `workrally-brand-film-pipeline`「平台唯一性规则」。
> 本地脚本只做纯计算；所有 I/O 由 Agent 走 MCP 完成。
>
> 迁移对照：`workrally tools call asset_search` → **`asset_search`**；`workrally canvas get` → **`canvas_manage(action="get")`**；`workrally canvas build-draft` → **`canvas_manage(action="build_draft")`**。
>
> **本技能是插件内「无限画布读写」的唯一规则源**：读取画布素材、`merge` 写入、画板分组排版、孤儿节点清理与 `draft_v1` 节点结构解析都以本文为准。`workrally-brand-film-pipeline` §9a 的项目归档只规定归档范围与验收口径，实际画布操作调用本技能。

## 适用场景

用户说"帮我整理一下画布"、"把图片和视频分画板"、"按类型把画布归类"等，并给出一个 WorkRally 画布链接或画布 ID 时使用。

画布链接形如 `https://workrally.qq.com/toolbox/canvas/<canvas_id>`。

## 🛡️ 核心安全铁律（不可违反）

> **1. 文字节点(text)和画笔节点(freehand)永远保留，不论任何情况、任何参数、任何用户指令都不动它们。**
>
> **2. 默认使用 `mode="merge"`（增量合并），只新增/重排媒资，不删除任何现有节点。**

展开说明：

1. **text / freehand 绝对保留** — 用户的笔记、手绘、旁白、分镜文字记录是其创作心血，任何整理/清理操作都必须跳过它们。这条是最高优先级约束，即使用户说"清空画布"、"全部删了"，也必须先与用户二次确认且**显式列出**要删除的节点清单。
2. **merge 模式优先** — 常规整理操作必须使用 `mode="merge"`，绝对禁止 `mode="overwrite"`（全量覆盖会毁掉所有未传入的节点）。
3. **媒资(image/video)可重排** — 素材资产通过重新写入坐标归位到新画板中，但不会删除素材本身（只是移动位置）。
4. **清理散落原节点** — 整理完成后如果用户要求"清理散落的旧节点"，可以**只针对散落的 image/video 孤儿节点**执行删除，必须硬编码排除 `type in ('text', 'freehand', 'artboard', 'generator')`。

### 技术说明

`asset_search` 只返回媒资（image/video/audio），不返回 text/freehand/artboard 节点。因此：
- 常规整理操作只影响**媒资的位置归位**（移入分类画板）
- 文字、画笔、原有画板**不受影响**
- **清理散落原节点**需要另行解析 `canvas_manage(action="get")` 返回的 draft_v1（Y.js update 二进制），详见 Step 7

## 新建项目画布与逐轮添加文件

`workrally-brand-film-pipeline` §0G 规定**什么时候**建画布、什么时候问用户；这里只写**怎么做**。

**新建**：

```text
canvas_manage(action="create", title="<项目名>_<YYYYMMDD>")
→ 返回 canvas_id 与「📎 画布前端链接」
```

链接直接用返回值，不自己拼。项目改名用 `canvas_manage(action="update", canvas_id, title)`，不新建第二个画布。

**建分区画板**（新建画布后同一轮做，一次 `build_draft(mode="merge")` 写完）：

- 8 个媒体分区从左到右排一行：02 人物CHAR、03 道具PROP、04 场景SC、05 视频单元、06 声音、07 字幕与包装、08 成片与验收、09 历史与未采用。00 项目导航、01 前期文字只有文字，放在最左边的文字栏，不建画板。
- 每个分区占三块：画板本身（宽 1530、高 800，一行放得下 4 个 340 宽的节点；放不下时生成接口会自动加高）、画板外上方的标题文字节点（y=-80）、画板右侧 900 宽的文字栏（放这一分区的提示词卡）。分区之间再留 200。画板只会向下加高，文字栏在右侧，两者都往下长，不会互相压住。
- 节点结构与 Step 4 脚本生成的画板和标题一致：画板 `{"id": "sec_02", "type": "artboard", "position": {"x": X, "y": 0}, "data": {}, "style": {"width": 1530, "height": 800}}`，标题 `{"id": "sec_02_title", "type": "text", "position": {"x": X + 40, "y": -80}, "data": {"text": {"content": "02 人物CHAR", "fontSize": 40, "fontWeight": 700, "color": "#ffffff"}}, "style": {"width": 600}}`。
- 写完 `get` 核对 8 个画板都在，把「分区编号 → 画板 ID」交给主流程记进 `canvas_sections`。

**生成结果直接落进分区**：`canvas_generate_image` / `canvas_generate_video` / `canvas_generate_audio` 同时传 `project_id=<canvas_id>` 和 `artboard_id=<分区画板 ID>`，占位节点就作为该画板的子节点排在空格子里，画板放不下会自动加高。返回的 `canvas_placeholder.artboard_id` 为空时，看 `message`：画板 ID 不对或不是画板，节点已按默认位置放在画布右侧，改用下文「整节点搬移」挪进分区，不另做副本。

**把用户同意的文件加到画布**（始终 `mode="merge"`）：

| 文件类型 | 做法 |
|---|---|
| 已在 WorkRally 生成的图 / 视频 / 音频 | 用 `asset_id` 建 `image` / `video` / `audio` 节点，`parentId` 设为对应分区画板，`position` 用相对画板的坐标；生成来的节点带 `data.task = {taskId, status:"success"}`，保留「再次编辑」 |
| 本机媒体文件（剪辑后的成片、字幕图层等） | 先按 `workrally-ref-image-cos-upload` 取 URL，再 `asset_create(project_id=<短番项目ID>, channel="toolbox_canvas", canvas_project_id=<canvas_id>)`，最后建节点放进对应分区 |
| Markdown / 提示词 / 字幕文本 | 建 `text` 节点，`data.text.content` 放全文，放在对应分区右侧的文字栏（文字节点不能进画板）；单卡上限 90000 字，超长按顺序拆成编号卡片，不截断 |

新节点只放进分区画板的空格子或文字栏的空白处，不覆盖已有节点；写完调 `canvas_manage(action="get")` 核对节点数量与类型，再告诉用户已添加。

## 最终推送画布：原有图片 / 视频节点和提示词必须保留

项目最终归档（主流程 §9a）以及任何整理项目画布的操作，都必须保留画布上**原有的图片、视频节点**和它们的**提示词**：

- **原节点不删、不替换、不改写**。原节点包括生成时自动落画布的占位节点（ID 形如 `mcp-<taskId>`，`data` 里带 `task` 与 `params`，即生成参数和提示词）、用户在网页里生成或拖入的图片 / 视频节点，以及此前逐轮添加的节点。
- **不对原节点 ID 做局部写入**。`build_draft` 写图片 / 视频节点必须带 `type` 和 `data`，而 `data` 是整块替换：只带位置和 `asset.id` 会把 `task` / `params` 冲掉。
- **新项目不做副本**：生成时传了 `artboard_id` 的原节点本来就在分区里，归档直接用。被替换或未采用的版本用「整节点搬移」移到 09 历史与未采用；搬不了就留在原处，旁边加「未采用」文字标签。
- **整节点搬移**（只用于 `data.task.status` 为 `success` 的原节点，生成中的不动）：先 `get` 解析出该节点的完整数据；`build_draft(mode="merge")` 写回同一个 `id`、原样的 `type`、`data`、`style`、`measured`，只改 `position` 和 `parentId`；写完再 `get`，逐字段核对 `data.task` 与 `data.params` 和写前一致。任何一步读不全或核对不上就停下，不搬。
- **旧画布才做副本**：本规则之前生成的、网页端生成或拖入的、没进分区的原节点，只给采用版本做副本放进分区，原节点留在原位。
- **最终归档不执行 Step 7 孤儿清理**。用户另行明确要求清理时，先说明会影响哪些原节点并二次确认；脚本已硬性保护 `mcp-` 开头的节点，另把项目台账登记的原节点 ID 通过 `--keep-ids` 传入。
- **提示词两处都保留**：原节点里的生成参数不动；同时按 §9a-2 在旁边放完整、可复制的提示词文字卡。副本节点只有 `asset.id`、没有生成记录，不能拿副本代替原节点作为提示词来源。

## 核心工作流

### Step 1：解析画布 ID

从用户给的 URL 或参数中提取 `canvas_id`：
```text
# URL: https://workrally.qq.com/toolbox/canvas/p9x251r3jsgima0uenm0dv3
# 取末尾路径段即可。
```

### Step 2：获取画布里的全部素材（MCP）

调用 **`asset_search`**。⚠️ 画布筛选必须**同时**传 `canvas_project_id` 与 `channel=["toolbox_canvas"]`：

```text
asset_search(
  canvas_project_id = ["<canvas_id>"],
  channel           = ["toolbox_canvas"],
  page_size         = 100,          # 默认 20，最大值 100
  page              = 1
)
→ {"assets": [{"id":..., "type":"image|video|audio", "title":..., "asset_details": {...}}, ...]}
```

把返回**原样落盘**（如 `/tmp/workrally/assets.json`）。若本页返回条数 = `page_size`，说明还有下一页，继续 `page=2,3...` 并把 `assets[]` 合并。
不传 `project_id` 也能查到。

### Step 3：按规则分类

根据 `title` 的**前缀/关键词**将素材分组。不同场景下规则不同，**需要先把所有 title 列给用户看，征询分类依据后再执行**。

典型规则示例（见 [references/classification-rules.md](references/classification-rules.md)）：

| 组名 | 匹配规则 |
|---|---|
| 🎭 人物资产 | title 含特定关键词（如"韩团风"、"清凉造型"、"角色"、"人物"） |
| 🎬 风格/分镜组 | title 前缀匹配（如"迪金斯·"、"太空电梯·"） |
| 🎥 视频库 | `type == "video"`，不考虑标题 |
| 📦 杂项 / 散图 | hash 命名、默认命名（`image.png`）、未匹配上的兜底组 |

### Step 4：用脚本生成节点 JSON 布局（纯本地）

```bash
python3 scripts/organize_canvas.py \
  --canvas-id <canvas_id> \
  --assets /tmp/workrally/assets.json \
  --rules rules.json \
  --captions \
  --output /tmp/workrally/canvas_nodes.json \
  --emit-args /tmp/workrally/build_args.json
```

布局参数：

- 网格单元尺寸：`CELL_W=280, CELL_H=280`
- 单元间距：`GAP=20`
- 画板内边距：`PAD=40`（左/右/下）、`PAD + TITLE_H=60+40`（上，为标题预留）
- 画板之间横向间距：`BOARD_GAP=200`
- 画板标题：独立 text 节点，放在画板**外部上方**（text 不能作为 artboard 子节点）

画板从左到右依次铺开，每板列数根据数量调整（一般 4 ~ 7 列）。
可选 `--captions` 为每个素材生成标题文字节点（caption），显示在素材下方。

### Step 5：增量合并写入画布（⚠️ 必须 `mode="merge"`，禁止 overwrite）

```text
canvas_manage(
  action   = "build_draft",
  canvas_id = "<canvas_id>",
  nodes    = <build_args.json 里的 nodes 数组>,
  mode     = "merge"
)
```

> `mode="merge"` = 增量合并，只新增/更新传入的节点，**不删除任何现有节点**。
> 同名 `id` 覆盖（修改）、新 `id` 追加（新增）、未提及的已有节点保留不动。
> 遇到版本冲突（code=3009）MCP 会自动重试。

### Step 6：校验

再调一次 **`canvas_manage(action="get", canvas_id="<canvas_id>")`**，看返回里的节点统计与类型分布，确认画板与素材数量符合预期。

### Step 7（可选）：清理散落的旧媒资节点（**文字节点绝不清理**）

**触发时机**：整理完成后，用户说"原节点除了文字都不用保留"、"清理散落的旧图片"、"把归类外的图片删掉"等。

**关键约束**：
- **项目画布最终归档不执行本步**（见上文「原有图片 / 视频节点和提示词必须保留」）
- `mcp-` 开头的生成原始节点、`--keep-ids` 传入的节点硬性保留
- 只删除 `type ∈ {image, video}` 且 `parentId` 为空（即不在任何画板内的孤儿节点）
- `text`、`freehand`、`artboard`、`generator` 类型**硬编码排除**，绝不删除
- 新整理产生的子节点都挂在画板下（`parentId` 非空），不会被误删

```text
① Agent 取快照（MCP）：
   canvas_manage(action="get", canvas_id="<canvas_id>")
   → 完整返回原样落盘 /tmp/workrally/canvas_snapshot.json

② 本地筛孤儿：
   python3 scripts/cleanup_orphans.py \
     --canvas /tmp/workrally/canvas_snapshot.json \
     --canvas-id <canvas_id> \
     --emit-delete-args /tmp/workrally/del_args.json

③ 目检清单后执行（MCP，逐批）：
   canvas_manage(action="build_draft", canvas_id="<canvas_id>",
                 delete_node_ids=<某一批>, mode="merge")
```

该脚本会：
1. 读 `canvas_manage(action="get")` 落盘的完整返回
2. 解析 `draft_v1`（Y.js update 二进制）扫描出所有节点 id/type/parentId
3. 筛选 `type ∈ {image, video} && parentId is empty` 的孤儿节点
4. **硬性排除 text/freehand**（无论 parentId 如何）
5. 分批（每批 ≤ 80）输出 `delete_node_ids`，交给 MCP `build_draft` 删除

> ⚠️ 若快照里找不到 `draft_v1`，说明落盘时被裁剪了。必须把 MCP `get` 的**完整返回**原样落盘。

## 快速调用

### 常规整理（安全，不删任何东西）

```bash
python3 <skill_dir>/scripts/organize_canvas.py \
  --canvas-id <canvas_id> \
  --assets /tmp/workrally/assets.json \
  --rules <skill_dir>/rules.json \
  --captions \
  --emit-args /tmp/workrally/build_args.json
# 然后 Agent 用 MCP canvas_manage(build_draft, mode="merge") 写回
```

### 整理后清理散落原节点（文字节点强制保留）

```bash
python3 <skill_dir>/scripts/cleanup_orphans.py \
  --canvas /tmp/workrally/canvas_snapshot.json \
  --canvas-id <canvas_id> \
  --emit-delete-args /tmp/workrally/del_args.json
# 然后 Agent 用 MCP canvas_manage(build_draft, delete_node_ids=[...], mode="merge") 删除
```

## 关键经验 / 踩坑

1. **`asset_search` 的画布筛选要两个字段一起给** —— `canvas_project_id=[...]` **必须**搭配 `channel=["toolbox_canvas"]`，否则筛不出来。
2. **`asset_search` 的 `page_size` 默认 20、最大 100** —— 画布素材多时要翻页并合并 `assets[]`。
3. **`canvas_manage(action="get")` 返回的 draft.content 总是 `"{}"`**，这是正常现象（节点在 Yjs 端）。真正的节点数据在 `draft_v1` 字段（base64 编码的 Y.js update）。
4. **artboard 子节点只能是 image/video/audio**，text/freehand/generator 都不能放画板里。因此 caption 文字节点使用绝对坐标放置在素材下方，而不是作为 artboard 的子节点。
5. **text 节点必须有 `data.text.content`**，用于画板标题时建议 `fontSize: 40, fontWeight: 700, color: '#ffffff'`。
6. **子节点不要设置 `extent: "parent"`**，否则被锁死拖不出画板（服务端会自动清除，但别写）。
7. **永远使用 `mode="merge"`**，禁止 `overwrite`。merge 是安全的增量操作。
8. **删除节点走 `delete_node_ids`**：`canvas_manage(action="build_draft", canvas_id=<id>, delete_node_ids=[...], mode="merge")`。每批建议 ≤ 80 个 ID 保险。**不要**用 `delete_node_ids` 与 `nodes` 混在一批里做危险操作。
9. **draft_v1 是 Y.js update 二进制**（非 gzip/msgpack/cbor），字段编码为 `<name_len><name>\x01w<varint_len><value>`。无需 Y.js 官方库，手写扫描即可提取 id/type/parentId。
10. **识别散落原节点**：新整理生成的媒资子节点 id 形如 `node_<uuid>` 且 `parentId=board_*`；原 UUID (36 字符) 节点且无 parentId 即散落孤儿。
11. **落盘 MCP 返回时不要裁剪** —— `canvas_manage(get)` 的完整返回里才有 `draft_v1`；只存 `content` 会导致 Step 7 无法解析。

## 相关参考

- [references/classification-rules.md](references/classification-rules.md) — 分类规则写法与 `rules.json` 配置格式
- [references/layout-cheatsheet.md](references/layout-cheatsheet.md) — 网格布局公式与常用尺寸

## ✅ 执行前告知用户

### 常规整理（非破坏性）

> 我要把画布整理成 N 个画板：A (x张) / B (y张) / ... 。**素材会被归位到新画板中，你现有的文字、画笔等内容完全不受影响。** 直接执行？

### 清理散落原节点（破坏性 — 必须二次确认）

> 我会删除 **X 个散落的原图片/视频节点**（未归类到任何画板的孤儿）。
> 🛡️ **已强制保留：Y 个文字节点 + Z 个画笔节点 + 所有分类画板及其子节点**。
> 是否执行？
