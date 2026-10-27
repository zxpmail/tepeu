# WorkBuddy 参考档案（逆向研究）

2026-10-27 提取。目标：连接件（Connector）与 skill 体系的打包与运行时思想。综合分析见 [ANALYSIS.md](ANALYSIS.md)。

身份：`@genie/workbuddy-desktop` v5.8.3（Electron 37.x，`version` 文件 37.10.3-42），Tencent，内部代号 genie，agent 运行时 codebuddy-lite。内容版权归腾讯，仅限内部研究勿公开分发。

## 提取路径（可复现）

| 轮次 | 路径 | 产出 |
|------|------|------|
| 1 | 安装目录 `C:\Users\zxpma\AppData\Local\Programs\WorkBuddy\resources\app.asar.unpacked\` | builtin 插件全量（skills/hooks/mcps/prompt fragments/extensions）、`cli/dist/codebuddy-lite-wb.mjs`（9.4MB 运行时 bundle） |
| 2 | 用户数据 `~/.workbuddy\` | 连接件市场物化（316 个）、`.skill-list-cache.json`（44 skill 清单）、`connectors/default/mcp.json`（腾讯系远端 MCP 预置，全 disabled）、sqlite 迁移链、audit-log |
| 3 | 官方市场目录 CDN `https://static.workbuddy.cn/connectors-config-v2/connectors-config.zip`（etag/sha256 见 `~/.workbuddy/.connectors-marketplace.meta.json`） | 443 连接件目录（85MB 解压，全量在 `%TEMP%/wb-connector-config/`，清单已入库 `catalog/connectors.json`） |
| 4 | app.asar 手写解析（npm @electron/asar 会因 unpacked 侧缺文件整体失败；头部布局：jsonLen@12、JSON@16、dataStart=8+u32@4）+ bundle 字符串窗口分析 | 运行时接线：PTC/REPL 架构、工具代理、权限链、计费闸门 |

## 产物索引

```
memory/workbuddy-reference/
  ANALYSIS.md                       综合分析
  catalog/connectors.json           官方市场目录（443 条，含 auth_mode/visible_in/examples）
  connectors/camscanner-mcp/        样本连接件（mcp.json + skills/SKILL.md + references/）
  builtin-skills/                   4 个生态自举 skill（skill-creator、marketplace-skill-installer、
                                    recommend-connectors、expert-manager）
  builtin-plugin/high-credit-gate.cjs   计费闸门 hook（479 行，注释即权限架构文档）
  builtin-plugin/miora-mcp/         builtin MCP 插件配置样本（.mcp.json + plugin.json）
  prompt-fragments/                 10 个提示词模板 fragment（记忆三层、通道路由、A2A）
  .skill-list-cache.json → skill-list-cache.json   skill 扫描缓存（含 filePath/source/type）
```

## 未入库（有意）

- 用户会话/聊天/审计数据（PII）：`~/.workbuddy/app/session`、`audit-log/`、sqlite 库
- `~/.workbuddy-key-fallback`（名字似密钥落盘）：未读未拷
- 市场目录 85MB 全量包：留 `%TEMP%`，可按 CDN URL + etag 重下
- 逆向方法论参考 `zhaoxuya520/reverse-skill`（安全任务 skill 路由包：scope.md 授权门、master-route、Evidence→Finding→Path）以只读方式用于方法论对照，留在 `%TEMP%/reverse-skill/`，未入库
