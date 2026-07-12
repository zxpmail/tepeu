# Design Brief — Tepeu Agentic OS

> 产品规格说明书兼视觉方向指引。dev-builder 读此文确定代码中的样式实现，design-maker 读此文生成 mockup。

---

## Product Summary

- **Product name**: Tepeu — Agentic Operating System
- **Product type**: Web (SPA, browser-based)
- **Target users**: 个人开发者（v1.0 主力）、企业团队（Phase 2+）
- **Core functionality**: 多面板 Web 工作台、Agent 流式对话、Workspace 管理、白盒记忆系统、文件管理、终端
- **UI Reference**: pi-web（会话管理、SSE 流式交互、文件浏览、分支对话）

## Design Discovery

| Field | Answer |
|-------|--------|
| Surface | Web |
| Primary audience | 个人开发者 |
| Tone | 专业克制 — 类似 VSCode / Linear |
| Brand context | 从零建立，无现有 logo/VI |
| Density default | 适中 — 类似 Linear，有呼吸空间但信息不失效率 |
| Theme | 双主题，跟随系统 |
| Motion | 极少 — 无动画瞬时切换，类 VSCode |
| Must-not | 无 |
| Reference pick | pi-web（主参考） |
| Success look | 专业 |

## Visual Direction Preset

- **Preset**: Custom (专业克制 + pi-web reference)
- **Notes**: 综合 Linear 信息密度 + VSCode 专业工具感 + pi-web 会话面板布局

## Design Direction

### Mood Keywords

- **专业** — 开发者工具，功能优先，不喧宾夺主。每个像素有目的，不装饰不过度设计
- **干净** — 多面板布局清晰，信息层级分明，无冗余视觉噪音
- **高效** — 工具即开即用，低认知负荷，操作路径最短

### Reference Products

| Reference Product | Liked Aspects | Disliked Aspects |
|------------------|---------------|-----------------|
| pi-web | 会话管理侧边栏设计、SSE 流式消息渲染、文件浏览面板、分支对话可视化 | 无（主参考） |
| Linear | 适中信息密度、深色/浅色主题一致性、极简但不简陋 | 项目/Issue 布局不完全适用（Tepeu 是多面板非列表为主） |
| VSCode | 侧边栏可折叠、面板布局效率、主题切换体验、瞬时应答感 | 图标/标签过于密集，需要适度放宽间距 |

### Negative References

- 无明确否决项

## Visual Specifications

### Color Direction

- **Theme Mode**: 双主题（深色 + 浅色），跟随系统
- **Color Temperature**: 中性偏冷（cool-neutral）
- **Brand Primary Color Direction**: 深色主题以深灰/黑为基底，蓝色系作为品牌锚定色（参考 pi-web 和 Linear 的蓝色基调）；浅色主题以浅灰白为底，同色系品牌色
- **Accent Color Direction**: 蓝色系（与品牌色一致），仅用于可交互元素和活跃状态
- **Brand Assets**: 无。从零建立（开发阶段先用占位）

### Information Density

- **Density Direction**: 适中（Moderate）
- **Reference Benchmark**: 接近 Linear 的信息密度 — 面板间有 16–24px 间距，内容区有呼吸空间，但不追求大面积留白
- **Rationale**: 多面板布局（工作台 + 聊天 + 文件 + 终端）需要平衡信息展示量与易读性。VSCode 过于紧凑，Notion 过于宽松，Linear 是合适的中间点

### Typography Direction

- **Font Character**: 几何无衬线（Geometric sans-serif），干净锐利
- **English Font**: Inter / System UI 字体
- **Chinese Font Preference**: 系统默认（PingFang/Source Han Sans）
- **Heading Style**: 标题与正文差异适度（标题 16–18px/600，正文 14px/400，辅助 12px/400）
- **Monospace**: 等宽字体用于代码块、终端输出、工具调用参数（JetBrains Mono / Cascadia Code）

### Interaction Style

- **Animation Level**: 极少（Minimal）— 无过渡动画，面板展开/折叠/切换瞬时完成
- **Transition Effects**: 仅 theme 切换有平滑过渡（背景色/文字色过渡 200ms），其余无动画
- **Overall Rhythm**: 直截了当，无延迟感。操作即响应

## Key Page Visual Notes

### 多面板工作台（Web Workbench）

- **Core Interaction**: 左侧 sidebar（Workspace 列表 + 文件树） + 内容主区 + 右侧面板（可切换显示聊天/记忆/终端）
- **Visual Direction**: 参考 pi-web 的侧边栏 + 主内容区布局。sidebar 可折叠（折叠后仅图标）
- **Special Requirements**: 面板可拖拽调整宽度；右侧面板通过 Tab 切换不同工具

### Agent 对话（Chat）

- **Core Interaction**: SSE 流式消息渲染，用户消息 → Agent 思考过程 → 工具调用 → 最终回答
- **Visual Direction**: 消息气泡风格（非纯文本），用户消息右对齐蓝色调，Agent 消息左对齐灰/白色
- **Special Requirements**: 工具调用以可折叠卡片展示（标题 + 参数摘要 → 点击展开详情）；推理过程用浅色引用块渲染；流式内容逐 Token 出现（typewriter 效果）

### 文件管理器

- **Core Interaction**: 目录树（左侧或内嵌） + 文件列表（右侧） + 预览面板
- **Visual Direction**: 类似 VSCode 文件资源管理器布局，但间距放宽到 Linear 风格
- **Special Requirements**: 拖拽上传区域、版本历史列表、文件内容预览（语法高亮、Markdown 渲染、图片）

### 记忆面板

- **Core Interaction**: 搜索栏 + 结果列表 + 单条详情/编辑
- **Visual Direction**: 类似笔记应用的列表风格，每条显示摘要 + 来源标签 + 时间戳
- **Special Requirements**: 来源可追溯（点击 §来源 跳转到原始对话）；tag 过滤为 chip 样式

### 终端

- **Core Interaction**: WebSocket 双向通信，Unix 命令执行
- **Visual Direction**: 深色终端主题（即使系统是浅色模式，终端面板保持深色底），等宽字体，参考 iTerm2/Warp
- **Special Requirements**: AI 辅助命令翻译（自然语言输入→shell 命令预览→用户确认后执行）；命令历史

## State Design

| 状态 | 方向 |
|------|------|
| 默认 | 数据正常展示，面板间分割清晰 |
| 空 | "尚无数据" + 引导按钮（如"创建第一个 Workspace""添加 API Key"），无插画 |
| 加载 | Skeletons 骨架屏（非 spinner），适配面板形状 |
| 错误 | 行内错误提示 + 重试按钮，无弹窗。SSE 流式中断：在消息流中插入错误块，不打断对话 |

## Anti-Slop Review

- ✅ 无渐变滥用 — 品牌色仅用于可交互元素，不出现"默认紫蓝对角渐变 Hero"
- ✅ 无玻璃拟态堆叠 — 面板间用分割线和背景色区分，不依赖半透明模糊
- ✅ 密度与功能一致 — 多面板产品采用适中密度，不强行大面积留白
- ✅ 字体层级明确 — 标题/正文/辅助/等宽至少 4 级
- ✅ 参考产品写明具体借鉴点（非仅产品名）
- ✅ 可执行 — 开发者可据此选 Tailwind 语义色值和组件风格

## Next Step Decision

| Field | Value |
|-------|--------|
| **User choice** | `dev-planner-first` — 先开发，mockup 后续补 |
| **Decided at** | 2026-07-10 |
| **Recommended default** | `/design-maker` — Brief is text-only until mockups exist |
| **If skip mockup** | Dev-builder implements from Brief only; UI drift risk noted |
| **Machine record** | `.forge/design-next-step.json` |
