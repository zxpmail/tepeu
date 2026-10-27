---
name: workrally-asset-qc
description: 对 WorkRally（或任意 figure/视频生成）批量产出的资产图做下载与验收质检（插件内下载与完整性校验的唯一实现）：串行下载 + PNG/JPEG/WEBP 完整性校验 + 按文件头真实格式命名、四角 100% 原尺寸裁切拼墙做水印/伪文字/徽标审计、按类别生成 contact sheet 供目视验收，并输出结构化验收记录。当用户说「验收资产」「QC 一下这批图」「检查有没有水印」「跑四角审计」「asset QC」「download and verify assets」，或 workrally-brand-film-pipeline 走到 §2f 资产验收环节时使用。仅做读取与校验，不修改、不重生成任何素材。
description_zh: 资产图批量验收与四角审计
description_en: Batch asset QC and corner audit
disable: false
agent_created: true
---

# workrally-asset-qc

> **平台唯一性**：受检素材只能是 WorkRally 生成并入库的资产，下载地址只来自 `asset_detail`。**不得**用其他生成服务、第三方 API、本地/开源模型或网页版工具的产物替换不合格项，也不得从外部图床补齐缺失资产。本技能只读不重生成；判为不合格后的返工仍由 WorkRally 重出（须用户确认）。完整规则见 `workrally-brand-film-pipeline`「平台唯一性规则」。

## When to use

在**资产图已生成、尚未进入视频生成**之间触发。典型触发语：

- 「验收这批资产」「QC 一下」「检查有没有水印/伪文字/图库徽标」
- 「跑四角审计」「生成 contact sheet」
- workrally-brand-film-pipeline 走到 **§2f 资产验收** 环节（CHAR / PROP / SC / CROWD 批量出图后）

**不适用**：单张图快速看一眼（直接用 Read 工具即可）；视频文件的验收（另走视频 QC）。

**核心原则**：这一步**只读不写**。只下载、校验、拼图、报告——**不修改、不重生成、不删除**任何素材。发现不合格项时**列清单交用户裁决**，不自动重跑（避免浪费生成额度）。

## Steps

### 1. 准备：确认清单与模型能力

先读资产台账（如 `assets_registry.json`），确认：
- 条目数、每条的 `code` / `file` / `download_url`
- 项目元数据（画幅、模型 ID、分辨率档）

**每次重新核验动态下发的模型列表**，不沿用上次的能力假设（分辨率档、参考图上限、quality 档都可能变）。

### 2. 建 Python 环境（一次即可，之后复用）

PIL 不在系统 Python 里，必须用受管 venv：

```bash
python3 -m venv ~/.workbuddy/binaries/python/envs/default
~/.workbuddy/binaries/python/envs/default/bin/pip install Pillow -i https://pypi.tuna.tsinghua.edu.cn/simple
~/.workbuddy/binaries/python/envs/default/bin/python -c "import PIL; print(PIL.__version__)"
```

### 3. 串行下载 + 完整性校验 + 按真实格式命名

> **本节是插件内「下载与完整性校验」的唯一规则源与唯一实现**。`workrally-brand-film-pipeline`、`workrally-batch-image-gen`、`workrally-game-art-flowchart-example` 等需要把 WorkRally 素材落盘时，一律先整理成 registry 再调本脚本，不各自写并发下载。

```bash
~/.workbuddy/binaries/python/envs/default/bin/python scripts/fetch_assets.py \
  --registry <项目>/assets_registry.json --out <项目>/assets
```

registry 最少字段：`{"assets": [{"code", "file", "download_url"}]}`；`download_url` 取 `asset_detail` 返回的 `asset_details.download_url` / `url`（签名 URL 有时效，过期先重取）。

**必须串行**。并行 curl 会静默截断大文件。脚本按文件头识别格式并逐项校验：
1. PNG：魔数 → chunk 遍历累积 `IDAT` → `zlib.decompress`，解压失败即损坏
2. JPEG：SOI/EOI 标记 + Pillow 完整解码
3. WEBP：RIFF 长度 + Pillow 完整解码

**文件名按文件头真实格式**：PNG→`.png`、JPEG→`.jpg`、WEBP→`.webp`，不按回执标签、`task_name` 或 registry 里写的扩展名；脚本会自动改名，并把真实文件名、格式、宽高、字节数与校验结论回写 registry。

不要用 `curl` 的退出码当作验收依据——它不校验内容完整性。

### 4. 四角裁切拼墙（水印 / 伪文字审计）

**两种粒度都要做**：

**(a) 单张四角** —— 每张图裁出四角 100% 原尺寸小图并排：

```bash
~/.workbuddy/binaries/python/envs/default/bin/python scripts/corner_audit.py assets/*.png assets/*.jpg assets/*.webp
```
产出 `qc/CORNER_<name>.png`。

**(b) 跨资产同角位拼墙** —— 把全部资产按同一角落拼成一张大图，一眼横比：

```bash
~/.workbuddy/binaries/python/envs/default/bin/python scripts/corner_wall.py assets/*.png assets/*.jpg assets/*.webp --outdir qc
```

（zsh 下无匹配的通配符会报错，按实际存在的扩展名传参。）

四个角各出一张（`qc/CORNERWALL_{TL,TR,BL,BR}.png`），每张把全部资产的**同一个角**按网格排开、每个图块下方紧贴烧文件名标签。可用 `--cols` 调列数、`--corner` 调裁切边长。

拼墙是最有效的一步：**同一角落横向对比**时，图库水印、圆形徽标、伪造签名会立刻显形。

**伪文字来源判定（必做，不可凭印象）**：先排除平台水印，再判定生成式残留——同一位置（尤其右下角）**只有个别图有、其余干净** → 生成式残留（图库原型召回），只列该张返工；**所有图都有** → 平台层水印，列为平台问题交用户，不建议逐张重出。判定结论写入验收记录。返工的提示词改法见 `workrally-brand-film-pipeline` §2f。

**必须用 Read 工具逐个目视这四张拼墙**，不能只看脚本退出码。

### 5. Contact sheet 分类验收

```bash
~/.workbuddy/binaries/python/envs/default/bin/python scripts/contact_sheet.py --assets assets --outdir qc
```
按类别拼图：CHAR（4 列）、PROP（4 列）、SC（1 列，因为宽幅场景缩小后细节丢失严重）。标签烧在左上角。

同样用 Read 工具目视。

### 6. 规格符合性逐项核对

按资产类型逐条比对（详见 `references/asset-specs.md`）：

| 类型 | 必查项 |
| --- | --- |
| CHAR | 左＝正面全身**裁头**、中＝含头背面全身、右＝3/4 侧脸特写；同人同服装；纯中性灰底柔光无强阴影 |
| CROWD | 单张 16:9 **多人同框**正面全身；头手脚完整不互遮；个体可区分；**不套主演裁头三视图** |
| PROP | 独立呈现，**无人物、无手、无剧情环境**；同款物件靠可辨识特征区分 |
| SC | 每个逻辑场景两张：主视图＋**独立顶视图**（正上方平面，不并进主视图）；**无人、无道具**（含倒影/投影/局部残留）；**无道具 ≠ 无建筑**——门柱/轨枕/管道/楼梯/砖墙必须保留 |

### 7. 输出结构化验收记录

写成 Markdown 表（建议回写进项目分镜文档的"资产验收记录"节）：

1. **校验结果**：N/N 通过、异常数、逐条尺寸与体积
2. **规格符合性**：按上表逐类结论
3. **水印审计**：四角拼墙目视结论，以及每处伪文字的来源判定（生成式残留 / 平台水印）
4. **需用户裁决的偏差**：逐条列出，**标注具体镜号影响范围**，并给出建议动作

在 `workrally-brand-film-pipeline` 项目里，验收记录交出后不直接进入下一步：按主流程 §2c-1，把这一类（CHAR / PROP / SC）的图和结论展示给用户，问「不修改，进入下一步 / 修改指定资产 / 这一类全部重做」，用户答「不修改」才继续。

## Pitfalls

- **并行下载会静默截断**。必须串行；且必须做完整性校验，不能只信 `curl` 退出码。
- **扩展名按文件头**。同模型不同批次可能返回 PNG 或 JPEG；`.jpg` 里装 PNG 属于命名错误，必须改成真实扩展名。
- **`sips -c` 裁切参数顺序极易搞错**（`-c H W --cropOffset Y X`），且产出异常大的错图。**裁切一律用 PIL `img.crop()`**。
- **PIL 必须装在受管 venv**，不要往系统 Python 装。用绝对路径调 venv 里的 python。
- **四角拼墙必须逐张用 Read 目视**。脚本跑成功 ≠ 没有水印。
- **SC 用 1 列拼图**。16:9 宽幅场景若缩进 4 列网格，细节全丢，等于没验收。
- **不要自动重跑不合格项**。生成要花额度——列清单交用户裁决。
- **不要因为"看起来像参考表"就默认合格**。SC 主视图可以是实拍式单机位；顶视图必须是正上方、结构可读的平面。缺顶视图、用斜俯拍代替、或把两张视图拼进一张图，都按规格不符合提报，不能默默放过。
- **状态版本要拆开验收**。同一角色有多个服装/道具状态（如戴盔 / 无盔）时，每个状态是一条独立资产，分别验收、分别登记。
- **剧本状态与生成状态要对齐**。例如场景里的可动构件（铁门开/合、门立着/倒伏）会随剧情变化——单张场景图只能锁一个状态，**发现覆盖不全时必须提报**，标注受影响的镜号区间。

## Verification

完成标准（缺一不可）：

- [ ] 每个资产文件的完整性校验通过（或明确列出损坏项），扩展名与文件头真实格式一致
- [ ] 四张跨资产同角位拼墙已用 Read 工具逐张目视
- [ ] 每类 contact sheet 已目视
- [ ] 规格符合性逐类给出结论（符合 / 不符合 + 具体理由）
- [ ] 所有偏差已列成**带镜号影响范围**的裁决清单
- [ ] 验收记录已写入交付文档

## 参考

- `references/asset-specs.md` —— CHAR / CROWD / PROP / SC 的完整规格、常见偏差、可动构件状态陷阱、验收结论模板
- `scripts/fetch_assets.py` —— 串行下载 + PNG/JPEG/WEBP 完整性校验 + 按真实格式命名（`--registry` / `--out`）
- `scripts/corner_audit.py` —— 单张四角 100% 裁切拼图
- `scripts/corner_wall.py` —— 跨资产同角位拼墙（水印审计主力工具）
- `scripts/contact_sheet.py` —— 分类 contact sheet

## 环境

全部脚本用受管 venv 的 Python 执行：

```
~/.workbuddy/binaries/python/envs/default/bin/python
```

不要用系统 Python（无 PIL），不要 `pip install` 到全局。
