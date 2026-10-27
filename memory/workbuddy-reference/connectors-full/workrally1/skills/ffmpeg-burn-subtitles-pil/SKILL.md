---
name: ffmpeg-burn-subtitles-pil
description: 在**缺少字幕滤镜的 ffmpeg 构建**上把字幕烧进视频像素（硬字幕），并完成「标题板 + 正片 + 片尾板」的成片合板。当用户要求烧字幕/硬字幕/把 SRT 压进画面，或本机 ffmpeg 报 `No such filter ass` / `subtitles` / `drawtext`，或需要把多段对白按时间轴排布成双语字幕时使用。核心方案：PIL 渲染透明 PNG 图层 → ffmpeg concat + 单次 overlay（不依赖 libass/freetype）。同时覆盖静音/无声视频的四轨混音与侧链闪避不可用时的电平差替代方案。注意：若只是外挂 SRT 或环境有 libass，请直接用 `subtitles` 滤镜，不要走本流程。
description_zh: 无字幕滤镜环境的字幕烧入
description_en: Burn subtitles via PIL layers
disable: false
agent_created: true
---

# ffmpeg-burn-subtitles-pil

用 **PIL 渲染透明 PNG 图层 + ffmpeg overlay** 的方式烧硬字幕，适用于 ffmpeg 构建**不含 libass/freetype/fontconfig** 的环境。

> **平台唯一性**：本技能是**本地后期处理**，只做字幕渲染与合成，**不生成画面内容**——这不构成"切换生成服务"。但输入的无字幕母版、画面与音轨素材只能来自 WorkRally，**不得**用其他生成服务、第三方 API 或本地/开源生成模型补拍、补图、补音轨，也不得用本地生成手段填补缺失镜头。缺素材时报告并停下等用户决定。完整规则见 `workrally-brand-film-pipeline`「平台唯一性规则」。

> **分工（单一规则源）**：本技能是插件内**字幕烧制实现**（图层渲染、合成、技术验收）的唯一规则源；字幕**标准**（版式、短句划分、语音驱动时间轴、同步验收门槛）以 `workrally-brand-film-pipeline` §6a-2 为准，时间轴证据来自该流程 §5a 的词级语音对齐。两者冲突时：标准听 §6a-2，烧制做法听本文。

## When to use

- 用户要求「烧字幕 / 硬字幕 / 把字幕压进视频 / 加字幕的成片」
- `ffmpeg -filters` 查不到 `ass` / `subtitles` / `drawtext`，或报 `No such filter: 'ass'`
- 需要**双语字幕**（如中上英下、各一行、底部居中、白字细黑描边）按语音时间轴逐句同步
- 需要把「片头板标题 + 正片 + 片尾板」拼成完整交付版
- **不适用**：只需外挂 SRT 文件；或环境有 libass（那就直接 `subtitles=xxx.srt` 一行搞定）

## 第 0 步（必做）：先探明本机 ffmpeg 的真实能力

```bash
ffmpeg -hide_banner -filters > /tmp/ff_filters.txt
ffmpeg -hide_banner -version | head -3   # 看 configuration 里有没有 --enable-libass / --enable-freetype / --enable-fontconfig
```
**用 Grep 工具检索 /tmp/ff_filters.txt**（注意：部分沙箱里 shell 的 `grep` 管道被禁用，会静默返回空 —— 不要据此判断「滤镜不存在」）。
确认这几个是否在：`fade` / `afade` / `anullsrc` / `concat` / `overlay` / `scale` / `format`。
**同时确认双输入音频滤镜**（见 Pitfalls 第 4 条）。

## Steps

### 1. 建时间轴 → 生成字幕母版（SRT + 一份机器可读 JSON）

输入是**已按 §6a-2 拆好的短句台账**（每条含 cue_id、父对白 ID、中英文、首尾词 ID，以及词级语音对齐得到的源片局部 `raw_start/raw_end`）。写一个 Python 脚本输出：
- `*_中文.srt` / `*_英文.srt` / `*_双语.srt`（供外挂）
- `*.ass`（文字母版存档）
- **`subs.json`**：`[{key, file, unit, raw_start, raw_end, start, end, cn, en}, ...]`，其中 `start/end` 为**成片时间** —— 后续两步都读它，**不要回头去解析 SRT/ASS**

**排布规则（关键）**：
- **时间只来自实测语音**：`raw_start/raw_end` 必须是词级对齐/原声核对的结果；**禁止用分镜计划时码、设计网格或按字数平均分配**填补缺失，缺失项标待核，不上屏假同步。
- **换算到成片时间只加一次偏移**：`film_t = timeline_in(unit) + (raw_t - source_in(unit))`（再加片头时长，若字幕叠在含片头的成片上）。`source_in/timeline_in` 取「最终剪辑对应表」的**实测**入出点；单元实测时长与计划不同（如 30.0417s vs 30s）时正是靠它吸收，不再用 `(unit-1)*GRID` 之类的设计网格公式。
- 必须 **全局串行** 排布，不能按单元分组后再串行 —— 否则跨单元的长句（L-cut）会让溢出字幕与下一单元首句重叠。
- 显示余量按 §6a-2：起点跟随开口、末尾余量 0–0.12s、不跨下一 cue；**不设统一最短时长或固定间隙**。交叠时把**前一条终点截到后一条起点**。
- 排完**打印校验**：重叠数、越界数必须为 0；列出所有跨单元台词与待核项。

### 2. PIL 渲染透明 PNG 图层（每句一张）

用 `scripts/gen_sub_overlay.py` 为每条 cue 渲染一张**全画布尺寸的 RGBA 透明 PNG**。

- 字体：**先验证再用**。macOS 候选：中文 `/System/Library/Fonts/STHeiti Medium.ttc`(index=1) 或 `/System/Library/Fonts/Hiragino Sans GB.ttc`(index 0=W3 常规、2=W6 加粗)；英文 `/System/Library/Fonts/HelveticaNeue.ttc`(index=0)。PingFang 在部分机器上不存在。对候选 `(path, index)` 跑 `truetype()` + `getname()` 并实际渲染一条中文看图；**加载失败必须报错，不要 `try/except` 回退 `load_default()`**（会把中文渲染成方块且不报错）。含中文的整串不要交给拉丁字体。脚本用环境变量 `CN_FONT/CN_INDEX/EN_FONT/EN_INDEX` 指定。
- 双语版式按 §6a-2：中上英下、各一行、底部居中；中文 ≈ `0.053×Ha`、英文 ≈ `0.039×Ha`（英文约为中文的 70%–80%），英文可见底缘距底 ≈ `0.03×Ha`，两行间隙 ≈ `0.008×Ha`；以固定参照字形定基线，避免逐条上下跳动。这些是起点，用原尺寸裁切 + 亮底对照校准。
- 描边靠 `draw.text(..., stroke_width=N, stroke_fill=(0,0,0,alpha))`。
- 居中要**用 textbbox 修正偏移**，不要直接用 `(W-w)/2`：
  ```python
  bbox = dr.textbbox((0,0), text, font=font, stroke_width=stroke)
  w = bbox[2]-bbox[0]; h = bbox[3]-bbox[1]
  x = (W-w)//2 - bbox[0]; y = bottom_y - h - bbox[1]
  ```
- 另存一张**全透明垫片** `transparent.png`（间隙用）。

### 3. 合成（两条路径二选一，时间轴规则相同）

**共同规则**：cue 窗口为帧级**半开区间** `[start_frame, end_frame)`（相邻 cue 不在边界帧双命中）；交叠先把前一条终点截到后一条起点；`overlay` 的 `t` 是**底片输入流**的时间，底片是正片局部流时用正片局部时码、是含片头成片时用成片时码，不混用；滤镜图写进文件用 `-filter_complex_script`，不塞命令行。两条路径都已实测可行，按图层数量与环境选择，不宣称某条普遍更好。

**路径 A：图层视频 + 单次 overlay**（`scripts/gen_sub_layer.py`）：cue PNG 与透明垫片按帧数交替 → `concat` 成一条图层流 → 对底片做**一次** `overlay`。脚本从 `ffprobe` 读底片宽高/帧率/时长，按帧量化并截断交叠，用 `trim=end_frame` 精确控制每段帧数（避免逐段秒数舍入累积漂移）。

```bash
PROJ=<项目根> BASE=<无字幕母版.mp4> OUT=<输出.mp4> python3 scripts/gen_sub_layer.py --tlimit=120   # 先出小样
PROJ=<项目根> BASE=<无字幕母版.mp4> OUT=<输出.mp4> python3 scripts/gen_sub_layer.py
```

**路径 B：多输入 overlay + enable**（实测 134 层、2206×946、17690 帧，耗时与无字幕编码同量级）：每张 cue PNG 各作一个 `-i`，链式 `overlay=0:0:enable='gte(t,S)*lt(t,E)'`（**半开**；不要用闭区间 `between`），S/E 取帧边界时刻：

```text
# filter.txt（-filter_complex_script 读取）
[0:v][1:v]overlay=0:0:enable='gte(t,12.375)*lt(t,13.750)'[v1];
[v1][2:v]overlay=0:0:enable='gte(t,13.750)*lt(t,15.125)'[v2];
...
[vN-1][N:v]overlay=0:0:enable='gte(t,S)*lt(t,E)',format=yuv420p[vout]
```

**⚠️ 输入索引从 1 开始**（`-i 底片` 占输入 0）——见 Pitfalls 第 1 条，两条路径都会踩。

### 4.（可选）标题板 + 正片 + 片尾板 合板

用 `scripts/assemble_final.py`：把标题板/片尾板（静态图）先编码成与正片**完全同参数**的片段（分辨率/帧率/pix_fmt/音频采样率声道一致），再用 concat demuxer **无损**拼接：
```bash
ffmpeg -f concat -safe 0 -i list.txt -c copy -movflags +faststart out.mp4
```
静态图转片段的 `-vf`：`scale=W:H:force_original_aspect_ratio=decrease,pad=W:H:(ow-iw)/2:(oh-ih)/2:color=black,fade=t=in:st=0:d=1,fade=t=out:st=<dur-0.8>:d=0.8,format=yuv420p`，并配 `-f lavfi -i anullsrc=r=48000:cl=stereo` 补静音轨。

## Pitfalls

1. **⚠️ 静默失败：`rc=0` 但字幕一帧都没烧进去 —— 输入索引 off-by-one。**
   `-i 正片` 是输入 **0**，图层段是输入 **1..N**。若循环里写 `[{k}:v]`（k 从 0 起），就会**把正片本身当成第 0 段图层** → concat 出的「图层」前段是原画，真字幕段被挤到片尾之外被 `-t` 裁掉。
   **必须写 `[{k+1}:v]`。**
   → **通用教训：ffmpeg 滤镜图不报错 ≠ 结果正确。`rc=0` 只证明语法通过。**

2. **合板必须做小样验证，不要直接跑全长。**
   给脚本加 `--tlimit=<秒>` 参数（只编码前 90–120 秒），几十秒出结果；抽全分辨率帧看底部区域确认字幕出现；通过后再跑全长。全长 600s/1080p 档约 4–5 分钟。

3. **不要用 shell 的 `grep` 判断 ffmpeg 能力。** 部分沙箱会静默拦截 `cmd | grep`，返回空 stdout 且退出码 0 —— 会被误读成「没有这个滤镜」。改成 `> /tmp/x.txt` 后用 Grep 工具检索。

4. **侧链闪避不可用（部分构建）**：`sidechaincompress` / `sidechaingate` 在 `filter_complex` 里可能**无法解析第二个输入 label**，报 `Error binding filtergraph inputs/outputs: Invalid argument`（rc=234）。最小复现可确认。
   → 替代方案：**静态混音 + 电平差**。例：对白 gain 1.9、终混 `amix=inputs=2:normalize=0,alimiter=limit=0.94:level=disabled`，对白 mean −16dB / 底噪 −30dB → SNR 14dB，对白清晰度够用。代价是底噪不自动下压。

5. **PIL 居中不要用 `(W-w)/2`**：`textlength`/`textbbox` 不含描边与 bearing，直接算会偏。用 `textbbox` 把偏移减掉。

6. **字体 index 要试**：`.ttc` 是字体集合，`STHeiti Medium.ttc` 的 index=1 才是 Medium；`index=0` 可能报错或给出另一种字重。

7. **跨单元长句（L-cut）**：若一句台词跨越单元边界，混音时两段必须各自只播自己那一段（`atrim=start=<负偏移>` 接续），**不能整句重复播放**，否则接缝处会出现叠字。

8. **「整带差分」不能当检出依据**：母版↔成片底部整带差分均值会被运动画面的重编码噪声淹没，实测无字间隙的差分反而高于有字 cue。检出用字芯掩膜法（见 Verification 第 2 条）。

9. **`select` 表达式不能太长**：`select='eq(n,A)+eq(n,B)+…'` 上百项会 `Error while parsing expression` / `Cannot allocate memory`（实测 196 项即失败）。分批（几十项以内）＋ `-ss`，或 `fps=N` 整段抽带后在 Python 里挑帧。

10. **不要用计划时码兜底**：词级时间缺失时标待核并定点重对齐，不回退到分镜计划时间或设计网格（§6a-2 禁止）。

## Verification

1. **字幕层**：抽帧用**原尺寸**裁出底部条带 + 亮底对照目检 —— 必须**肉眼看到字**，检查字形、方块、行隙与位置。只看 `rc=0` 或文件体积不算验收。
2. **逐条检出（字芯掩膜法）**：对每条 cue，从其 PNG 取 `alpha == 255` 的像素作字芯掩膜，在成片该 cue 中点帧同位置测亮像素占比，并把同一掩膜套在**无字幕母版**同帧作阴性对照；成片应接近 1、母版应接近 0。间隙时段用前后 cue 的掩膜检查零残留。低分 cue 原尺寸目检定性。
3. **时间轴**：断言「重叠=0、越界=0」、半开区间、交叠已截断；末句结束时间 ≤ 片长；帧数与母版一致，音频 `-c:a copy` 未改动。
4. **同步**：按 §6a-2 验收门槛连续带声回放核对短句起落；掩膜检出只证明字在画面上，不证明同步正确。
5. **L-cut 接续（信号级，硬证据）**：对每条跨单元台词，取**目标单元首段音频**与其源音频「应播区段」做**归一化互相关**，峰值应落在 lag≈0、NCC 显著（实测可达 0.99）。短窗（<0.4s）把搜索范围收到 ±50ms 并放宽最小窗口，否则相关函数会因样本不足返回哨兵值（如 −2.0）被误判为失败。
6. **成片规格**：`ffprobe` 核对 duration / 分辨率 / 帧率 / 音频采样率声道数，与源一致。
7. **合板段**：抽头板、正片首帧、片尾板各 1 帧拼版，确认淡入淡出与三段衔接无黑帧、无错位。

## 参考脚本

> 以下脚本源自一次真实项目（21:9 / 600s / 双语字幕）。`gen_sub_overlay.py` 与 `gen_sub_layer.py` 已改为环境变量配置（`PROJ`、`BASE`、`OUT`、`W/H`、字体），并按 §6a-2 版式与帧级半开区间实现；`verify_lcut.py`、`assemble_final.py` 仍含项目常量，复用时改开头常量。

- `scripts/gen_sub_overlay.py` — subs.json → 逐条透明 PNG 图层（§6a-2 版式，字体加载失败即报错）
- `scripts/gen_sub_layer.py` — 路径 A：PNG 序列 → 帧级图层视频 → 单次 overlay（含 `--tlimit` 小样通道）
- `scripts/verify_lcut.py` — 跨单元对白接续的信号级互相关验收
- `scripts/assemble_final.py` — 标题板 + 正片 + 片尾板 同参编码 + 无损 concat
