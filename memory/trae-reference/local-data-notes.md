# Trae 本地数据层分析（第三轮：服务端提示词追踪）

日期：2026-10-22。目标：验证"主对话系统提示词是否在本地"。

## 结论

**主对话系统提示词不在本地明文中。** 证据：

- `%APPDATA%/Trae CN/logs/`：无 "You are"/"你是" 主提示词命中
- `%APPDATA%/Trae CN/ModularData/ai-agent/database.db`（4.6MB，对话库）：**加密**（随机字节头，非 sqlite 明文）
- workbench `out/vs`、`agent-tool-host.exe`：均无主系统提示词（前两轮已验证）
- 剩余路径仅抓包（且 Trae 网络层为 `sscronet.dll` = 字节 TTNet/Cronet fork，可能绕系统代理 + 证书校验，成功率存疑）

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

## 用户已装社区 skill（顺带记录）

`~/.trae-cn/skills/`：design-md、enhance-prompt、excalidraw-diagram、react-components、remotion、shadcn-ui、stitch-design、stitch-loop、taste-design、ui-ux-pro-max —— 与 tepeu 用户的 Claude skill 集高度重叠（社区流通的同一批）。
