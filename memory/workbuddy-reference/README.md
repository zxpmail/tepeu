# WorkBuddy 参考档案（逆向研究）

2026-10-27 提取。目标：连接件（Connector）与 skill 体系的打包与运行时思想。综合分析见 [ANALYSIS.md](ANALYSIS.md)。

身份：`@genie/workbuddy-desktop` v5.8.3（Electron 37.x，`version` 文件 37.10.3-42），Tencent，内部代号 genie，agent 运行时 codebuddy-lite。内容版权归腾讯，仅限内部研究勿公开分发。

## 提取路径（可复现）

| 轮次 | 路径 | 产出 |
|------|------|------|
| 1 | 安装目录 `C:\Users\zxpma\AppData\Local\Programs\WorkBuddy\resources\app.asar.unpacked\` | builtin 插件全量（skills/hooks/mcps/prompt fragments/extensions）、`cli/dist/codebuddy-lite-wb.mjs`（9.4MB 运行时 bundle） |
| 2 | 用户数据 `~/.workbuddy\` | 连接件市场物化（316 个）、`.skill-list-cache.json`（44 skill 清单）、`connectors/default/mcp.json`（腾讯系远端 MCP 预置，全 disabled）、sqlite 迁移链、audit-log |
| 3 | 官方市场目录 CDN `https://static.workbuddy.cn/connectors-config-v2/connectors-config.zip`（etag/sha256 见 `~/.workbuddy/.connectors-marketplace.meta.json`） | 443 连接件目录（85MB 解压，全量在 `%TEMP%/wb-connector-config/`，清单已入库 `catalog/connectors.json`） |
| 4 | app.asar 手写解析（npm @electron/asar 会因 unpacked 侧缺文件整体失败；头部布局：jsonLen@12、JSON@16、dataStart=8+u32@4）+ bundle 字符串窗口分析 | 运行时接线：PTC/REPL 架构、工具代理、权限链、计费闸门、子代理契约 |
| 5 | 安装目录 `resources/app.asar.unpacked/resources/plugins/workbuddy-builtin/{interactionmode,welcomemode}`（同日补充拷贝） | 5 交互模式 + 3 欢迎场景的 plugin.json + fragments（45 文件） |

## 产物索引

```
memory/workbuddy-reference/
  ANALYSIS.md                       综合分析
  catalog/connectors.json           官方市场目录（443 条，含 auth_mode/visible_in/examples）
  catalog/connectors-config.zip     官方 CDN 全量包（34.7MB，sha256 099d10b2…d780e15，
                                    Last-Modified 2026-10-20；85MB 解压，含 references/与全部资源）
  connectors-full/                  市场全量知识层：443 连接件的 SKILL.md（1005 个）+ mcp.json/plugin.json
                                    （~1400 文件，15MB）。references/ 与二进制看上面的 zip
  connectors/camscanner-mcp/        样本连接件全文（SKILL.md + references/，保留作完整形态对照）
  builtin/                          builtin 全文镜像（skills/ 21 个 + builtin-plugins/ 10 插件的
                                    skills+experts，61 SKILL.md，纯文本 ~5MB；含 workbuddy-docx
                                    experts/ 的 9 个专家包实物）
  builtin-plugin/high-credit-gate.cjs   计费闸门 hook（479 行，注释即权限架构文档）
  builtin-plugin/miora-mcp/         builtin MCP 插件配置样本（.mcp.json + plugin.json）
  prompt-fragments/                 10 个提示词模板 fragment（记忆三层、通道路由、A2A）
  interactionmode/                  5 个交互模式插件全文（ask/craft/expert/plan/quick，
                                    plugin.json + fragments/）——模式 = 提示词切片
  welcomemode/                      欢迎场景分组（code/design/work）
  skill-list-cache.json             skill 扫描缓存（含 filePath/source/type）
  notes-subagent-baas-memory.md     专项笔记：子代理 Agent/TaskOutput 契约、记忆三层
                                    L2/L3、BaaS=glass-easel、交互模式对比
```

学习入口建议：生态自举四件套 `builtin/skills/{skill-creator,marketplace-skill-installer,recommend-connectors,expert-manager}` → 专家包实物 `builtin/builtin-plugins/workbuddy-docx/experts/` → 连接件形态 `connectors/camscanner-mcp/` → 海量横向样本 `connectors-full/` → 专项深读 `notes-subagent-baas-memory.md`（子代理/记忆/BaaS/交互模式）。

## 未入库（有意）

- 用户会话/聊天/审计数据（PII）：`~/.workbuddy/app/session`、`audit-log/`、sqlite 库
- `~/.workbuddy-key-fallback`（名字似密钥落盘）：未读未拷
- 市场目录 85MB 解压树：留 `%TEMP%/wb-connector-config/`（zip 已入库 `catalog/`，解压一条命令可复原）
- **版本漂移实证**：客户端 `~/.workbuddy/.connectors-marketplace.meta.json` 锚 sha256 `aafdc00c…`/etag `1683…`，CDN 现行是 `099d10b2…`/etag `74a7…`（Last-Modified 2026-10-20）——客户端停在旧版未重拉，与本机物化 316 个 vs 现行目录 443 个的差吻合
- 逆向方法论参考 `zhaoxuya520/reverse-skill`（安全任务 skill 路由包：scope.md 授权门、master-route、Evidence→Finding→Path）以只读方式用于方法论对照，留在 `%TEMP%/reverse-skill/`，未入库
