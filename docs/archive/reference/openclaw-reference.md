# OpenClaw × Tepeu

> **地位**：存档备查。⑤ 本机个人/团队助手（Gateway + 频道），**不是** Java OS。不进底板。  
> **来源**：https://github.com/openclaw/openclaw。2026-08-31 读 README + [Why OpenClaw](https://docs.openclaw.ai/start/why-openclaw)（对照 Hermes，源评 2026-08-27）。未 clone、未跑。  
> **已见**：安全系列把 OpenClaw 词法白名单当反面；正典仍是 syscall + Policy + Sandbox。

Gateway 管会话、频道、凭据、策略；执行可挪到沙箱 / node / 云工人。口号：可信控制面、不可信执行、策略在代码里。默认沙箱关，主会话工具跑在宿主机。审批绑命令 + cwd + 环境哈希 + 文件哈希，漂移就拒。频道陌生人先 pairing。密钥用 SecretRef，模型侧看句柄。

**能吸收的：没有。** 七条企业性质你们已经冻了同形：Policy 在代码、deny 赢、无审批 UI 则拒、子权限只减、密钥不进 compose、账本是事实。审批绑 `argsDigest` 已是同一刀，不因他们多绑 cwd/env 再挂账。

| OpenClaw | Tepeu | 吸不吸 |
|----------|--------|--------|
| Gateway 可信 / 执行可挪走 | host+compose / execution jail | 已有。他们默认沙箱关，不抄 |
| 策略在代码，deny 赢 | `deny > ask > allow` | 已有 |
| 批准绑命令+cwd+env+文件 | 审批绑 `argsDigest` | 已有。加厚是 execution 细节，不新开 |
| 工具策略按名，副作用归沙箱 | Policy 管 syscall，隔离在 spawn | 已有 |
| 密钥句柄，egress 才替换 | compose 不读密钥 | 已有 |
| 工人只见本轮窗，账本在 Gateway | `Observation.view` / entries | 已有 |
| pairing / 频道 / 插件市场 | ⑤ | 不 |
| 字符串 exec 白名单 | 安全系列已记词法坑 | 反面，不重挂 |

反面：默认宿主机 exec；用词法白名单当边界。

不新开 ADR / jar / §8.5。
