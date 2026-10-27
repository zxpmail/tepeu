# 引擎能力边界明细（S2 生成 HTML 参考）

转换引擎对 HTML 的能力边界完整速查。硬性禁用项已内联在 `doc-formatter.md` §2.5「引擎不支持」清单，本文件为完整明细。

| 能力 | 状态 |
|---|---|
| 封面（`<header>` 无 class） | ✅ 支持 |
| 章标题（`.section-header` 内联底色 → 单格表格） | ✅ 支持 |
| 容器样式下传（`.research-body`） | ✅ 支持 |
| 表格（caption / 百分比宽 / vAlign / 行高 / 嵌套 / 合并单元格） | ✅ 支持 |
| 表格来源注释/口径说明用独立 `<table>` 承载 | ❌ 相邻表格视觉合并——注释用带浅色底纹的段落（如 `.src-note`） |
| 组件（`data-component`：callout / data-card / data-card-grid / divider / section-marker） | ✅ 支持 |
| 图片（本地路径 / dataURI / 坏图降级占位 / 图文混排） | ✅ 支持 |
| 段落级背景色（通用块 → 单格表格） | ✅ 支持 |
| 数学公式：HTML `<math>`（MathML） | ❌ 转换丢弃——公式位置保留含编号与 LaTeX 源的占位段，公式由转换侧方程能力插入 |
| 页眉页脚：CSS `@page`（6 个 margin-box、PAGE/NUMPAGES/STYLEREF 域、updateFields） | ✅ 支持（见 doc-formatter.md §2.5 @page 规范） |
| 语义 section 分节（顶层 `<section>` → 独立节 / `data-orientation` 横向 / `data-page-restart` 页码重起） | ✅ 支持 |
| 精确页面边距（`margin_*`） | ❌ 暂不由 HTML 承担（需另走文档服务页面设置工具） |
| `@page :first` / `@page { size }` 扩展语法 | ❌ 引擎忽略（不写） |
| callout 的 `data-title` | ❌ 不支持，标题走 `callout-content` 内加粗 |
