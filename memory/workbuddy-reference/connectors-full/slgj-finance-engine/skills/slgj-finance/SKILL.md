---
name: slgj-finance
description: 丝路E投财务引擎使用指南——投融资项目快速测算、报告出稿、勾稽体检、指标反算。首次会话必须先让用户阅读接入总则（协议门禁），数字一律引用引擎返回原文。
---

# 丝路E投财务引擎 · AI 使用说明

本连接器通过 MCP 调用丝路E投财务引擎（https://www.slgj.cn ）。所有计算在平台侧完成，AI 只负责组织用户输入与解读返回结果。

## 〇、铁律（每次会话开始前确认）

1. **首次调用任何业务工具前，必须先调 `get_protocol_instructions`**——否则平台返回 428 门禁拦截（72 小时内有效，过期重读）。
2. **数字纪律**：所有数值一律引用工具返回的原文（可四舍五入到两位小数），禁止自行换算、编造或心算。出稿类工具会自动做数字值域比对。
3. **测算结论必须附带免责说明**：结果基于用户输入与现行财税口径，仅供参考，不构成投资承诺。
4. 用户令牌失效（401/鉴权错误）→ 引导用户到 www.slgj.cn 「个人中心 → AI接入」重新生成令牌，并在连接器设置里更新。

## 一、快速测算主链（最常用）

**输入**：五类 txt 文件（E投原生格式，全文粘贴）——project_basic_info / construction_data / revenue_data / cost_data / financing_data。

| 需求 | 工具 | 说明 |
|---|---|---|
| 只要报表和指标 | `fast_calc_reports` | 返回 11 张报表+指标+IRR/MIRR 诊断+资金缺口。约 50-60KB，可传 `tables` 参数分批拉取；返回末位 `complete:true` =完整 |
| 要 Excel 成品 | `fast_calc_excel` | 12 表全公式 Excel（E投版式+勾稽自检页），72h 短链 |
| 四件套一次到位 | `run_delivery_bundle` | Excel+看板+敏感性+可选 Word，约 25-30 秒，客户端超时建议 ≥60 秒 |

**五类数据不全时**：让用户按 nlmodel 模板逐项提供（`get_skill_instructions` 可拉模板），或用 `run_natural_language_model` 从自然语言直接测算（L3 关键大数缺失会反问）。

## 二、报告出稿

1. **财务口径报告**：`generate_word_report`，`report_type=econ`（经济评价 6 章）或 `feasibility`（可研 8 章）；先 `get_chapter_data` 取章节标准目录与引擎值，正文数字用 `{{章节.键}}` 占位符直灌（推荐，零抄写漂移）或引用返回原值。
2. **国民经济评价报告**：先 `run_national_econ` 真算指标（ENPV/EIRR/BCR），再 `generate_word_report` 用 `report_type=national` + `national_data`（benefits/costs/rate 与测算输入同构），10 章标准目录+5 张专项附表自动组装。
3. 章目录与标准不符会被拒收并返回标准目录——照返回的目录改即可。正文段落每段 150-250 字完整论述，末章必须含标准免责句（模板里有原文）。

## 三、质量与诊断工具

- `run_model_check`：报表勾稽体检（六段 72 项；❌=数据风险须如实告知用户，⚠️=提示）。
- `fast_calc_solve`：指标反算（14 项目标）。IRR 因现金流多次变号失灵时，按返回的等价改法改用 NPV 目标（先把基准收益率设为目标值，再 target=npv_*、goal=0）。
- `run_uncertainty`：单因素敏感性+多情景（quick=true 约 20 秒）。
- `run_revenue_review`：收入费用审查。
- `estimate_match` / `estimate_excel`：造价指标匹配与投资估算。

## 四、常见错误与恢复

| 现象 | 处置 |
|---|---|
| 428 门禁 | 先调 `get_protocol_instructions`，读完重试 |
| 401/鉴权失败 | 令牌过期或吊销——引导用户到 slgj.cn 个人中心重新生成并更新连接器配置 |
| 响应疑似截断 | 查返回末位 `complete:true`；缺失则用 `tables` 参数分批拉取 |
| 生成超时 | 报告类工具耗时较长（30-90 秒），连接器已配 180 秒超时；仍超时则拆单工具调用（先 reports 后 word） |
| 反算「IRR 失灵」 | 按 message 里的等价改法改 NPV 目标 |

## 五、交付要求

- 成品一律以返回的 `file.url` 短链交付（72 小时有效），把链接直接给用户。
- 报告类交付必须附免责句；测算类交付建议附「结果基于用户输入测算，仅供参考」。
- 遇到无法解释的失败，用 `submit_feedback` 转录用户反馈（免计费），并告知用户问题已记录、平台会处理。
