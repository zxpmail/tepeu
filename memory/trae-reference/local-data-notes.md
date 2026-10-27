# Trae 本地数据层分析（第三轮：服务端提示词追踪）

日期：2026-10-22。目标：验证"主对话系统提示词是否在本地"。

## 结论

**主对话系统提示词不在本地明文中。** 证据：

- `%APPDATA%/Trae CN/logs/`：无 "You are"/"你是" 主提示词命中
- `%APPDATA%/Trae CN/ModularData/ai-agent/database.db`（4.6MB，对话库）：**加密**（随机字节头，非 sqlite 明文）
- workbench `out/vs`、`agent-tool-host.exe`：均无主系统提示词（前两轮已验证）
- 剩余路径仅抓包（且 Trae 网络层为 `sscronet.dll` = 字节 TTNet/Cronet fork）。**2026-10-23 更新：mitmproxy 抓包已打通**（要点：改 Trae 自身代理设置为 manual + CA 进用户 Root 库），见文末"服务端路由抓包"节

## 意外收获 1：builtin_skills（注册表之外的第 27-31 个 skill）

`%USERPROFILE%/.trae-cn/builtin_skills/`（960KB，24 文件）→ 已拷至 `builtin_skills/`：

- **TRAE-generate-mini-app**：内含 `prompts/system-prompt.md` —— **完整中文系统提示词实物**（"你是指挥 Vibe Coding 平台进行小程序开发的顶级架构师…"，`<base_template>`/`<ui_ux_design_principles>` XML 标签结构，Taro 4.x + React 18 + 750rpx 设计稿约束）
- TRAE-code-review、TRAE-debugger（含 guides/ 五篇 + python debug-server 工具）、TRAE-security-review、_shared（knowledge 格式 + git-mining 参考）

## 意外收获 2：模型路由表（32 个模型）

来源：`%APPDATA%/Trae CN/User/globalStorage/state.vscdb` → ItemTable key `3695902117082308:AI.agent.model.model_list_map`。

路由结构：按 agent 形态分组（assistant / agent / solo_agent / solo_coder / solo_work_lite / solo_design_lite / *_remote），每形态配可用模型集 + 每模型带 context window、计费等级、思考开关、max_turns（默认 500）等运行参数。

| 家族 | 模型 | ctx |
|------|------|-----|
| 字节 Seed（默认 **Seed-Code**） | Seed-2.1-Pro/Turbo、Seed-Code、Seed-Evolving | 256K-1M |
| DeepSeek | V4-Flash / V4-Pro / V4.1-Flash | 1M |
| 智谱 GLM | GLM-5 / 5.1 / 5.2 / 5.3 / 5.3-Flash / 5.3-FlashX / 5V-Turbo | 200K-1M |
| Moonshot | Kimi-K2.5 / K2.6 / K2.7-Code / K2.8-Preview / K3 | 200K-1M |
| 小米 | MiMo-V2.6-Flash / Pro | 981K |
| MiniMax | M2.7 / M3 / M3.1-Flash | 200K-1M |
| 阿里 | Qwen3.5/3.6/3.7-Plus、Qwen3.8-Flash/Max | 1M |
| 阶跃 | Step-5-Preview | 1M |

`gpt_54` skill 渠道在此表中无对应项（表中全为国产模型 + 无 GPT/OpenAI 条目）——该渠道可能面向海外版或历史版本，**修正前一轮推断**。

## 模型路由表与提示词的关联（字段级证据）

`model_list_map` 每个模型条目（约 50 字段）携带**按模型差异化的提示词/行为开关**——表本身无提示词文本，但它是服务端拼提示词的参数面：

- `native_function_call: true`：全部 32 模型均开——工具以原生 function-call schema 进请求，而非文本序列化（提示词结构级差异）
- `enable_invalid_json_hint: true`：全部开启——模型输出坏 JSON 时注入修复提示
- `is_new_pe: true`：全部开启（推断：new prompt engineering 版本旗标）
- `open_optimize: true`：**仅字节自家 Seed 系**（Seed-2.1-Turbo/Pro、Seed-Evolving、Seed-Code、Seed-2.0-Code）——自家模型才开的优化路径
- `parallel_tool_calling: false`：**仅 Seed-2.1-Pro 显式关闭**——唯一并行工具调用差异点
- `prompt_max_tokens`（如 224000/256000）：系统提示词+工具定义+历史的总预算
- `enable_todo_thought_extract` + `todo_thought_extract_threshold`：从模型思考流提取 todo 的解析开关
- `reasoning_effort_config/options`、`thinking_enable`、`temperature/top_p/top_k`：推理与采样参数

结论：提示词文本在服务端按 (agent 形态 × 模型 × skill 渠道) 拼装；本地路由表提供拼装参数（开关+预算+采样），`open_optimize`/`parallel_tool_calling` 两处按模型差异是"每模型一套运行时策略"的直接实证。

## 用户已装社区 skill（顺带记录）

`~/.trae-cn/skills/`：design-md、enhance-prompt、excalidraw-diagram、react-components、remotion、shadcn-ui、stitch-design、stitch-loop、taste-design、ui-ux-pro-max —— 与 tepeu 用户的 Claude skill 集高度重叠（社区流通的同一批）。

## 服务端路由抓包（2026-10-23，第四轮）

来源：mitmproxy 截获 `api5-normal.mchost.guru/api/ide/v1/get_skill_detail` 响应，存 `model-skill-routing-v2.json`。上面"成功率存疑"作废：sscronet 不理会环境代理，但改 Trae 自身设置 `trae.network.proxy.effectiveMode: "manual"` + 指向 mitmproxy 后全量可解（CA 进用户 Root 库）。

结构：`meta` 平铺 + `new_meta` 按 agentType 嵌套（code / design / trae / work）两套并存。agentType 折叠：solo_agent→trae；solo_agent_lite/remote→code；solo_design_lite/remote→design；solo_work_lite/remote→work。

- 模型 60 个（上面的本地表 32，本地快照落后于线上）：增量主要是 16 个 `custom_model_*` 槽位（用户自带模型），平铺层全部 `default`
- 每模型一个渠道 tag：`default` / `non_multimodal` / `doutops`（Seed 代码线）/ `seed`（design、work 的 Seed 线）/ `r4_handoff_v1`（仅 trae 形态：DeepSeek-V4-*-Official、deepseek-v4.1-flash、glm-5.3、glm-5.3-flash）/ `hebe`（kimi-k2.6，code/work）/ `iphigenia`（aquila，仅 work）/ `iris`（仅 work 的 minimax-m3 系）
- 同模型跨形态换渠道：`glm-5.3-flash` = non_multimodal(code/work) / r4_handoff_v1(trae)；`Doubao-Seed-2.1-Pro` = doutops(code/trae) / seed(design/work)；嵌套层里 `custom_model_doubao_*` 在 code 组落 `doutops` 而非 `default`
- `-auto` 后缀模型（Doubao-Seed-2.0-Code-auto、Seed-2.1-Turbo-auto、kimi-k3-auto、doubao-for-auto）落 `default`；唯一例外 `Doubao-Seed-Evolving-auto` 跟随主模型落 `doutops`
- `version_lock_tag: 20261014152702_external_prod_skill_version`：skill 版本锁，对应注册表 channel 四段的"构建批次"，服务端下发

意义：skill 渠道变体按 **(agent 形态 × 模型)** 二维分发，不是只按模型；`get_skill_detail` 一次抓包即可枚举线上全部路由，不必再依赖本地 state.vscdb 快照。
