# 自托管 AI 评测可观测管线 × Tepeu（短对账）

> **地位**：存档备查。**不是**内核规范；**不是**下一刀排期。  
> **来源**：[Building a Secure Self-Hosted Observability Pipeline for an AI Evaluation Platform](https://dev.to/starkprince/building-a-secure-self-hosted-observability-pipeline-for-an-ai-evaluation-platform-53b0)（Prince Raj / DEV.to，2026-08-24）。  
> **日期**：2026-08-24。

---

## 总判断

| | 该文 | Tepeu `os/` |
|--|------|-------------|
| 层 | 边缘接入 · 私有 VM · OTel/Phoenix 导出 | syscall · Policy · 三 store · Loop |
| 活着的 | 安全可见管线 | 本机 Agent OS 骨架可演示 |
| 未做（文自承） | 评测作业持久化、配额、CI 门禁、项目治理 | 记忆平面、⑤ UI、处置链等（见 gap） |

**同线可借：姿态与纪律。不同线：Cloudflare / Caddy / Phoenix / ACR 拓扑。**  
勿开 `observability/` jar；勿用本文验收 Context/Observation/Gate。

---

## 可借（不立项）

1. **先安全可见，再接真实负载** — 合成非敏感 trace 验通管线，再引入真实 agent 内容。  
2. **遥测默认不带** prompt / 模型输出 / 密钥 / Authorization — 导出面字段白名单；对齐「日志禁明文 secret」、人手审计归 AuditSink。  
3. **活着什么 / 还没有什么写死** — 与 gap「不许暗示已具备」同向。  
4. **密钥不进仓、最小托管身份、镜像 digest** — 发行/运维面；与 compose「不读 API 密钥」同向。  
5. **多层纵深** — 单控失效不裸奔（类比 Policy≠Sandbox、总线多闸——仅姿态）。

## 不借

- Tunnel / Access / Phoenix 当 `os/` 组件或插头正典  
- ProjectionBus ≈ Phoenix UI  
- 评测队列 / DeepEval worker 插队 develop 痛点  
- 把「可观测」混成「上下文管理」组件（进窗算法 ≠ 边缘遥测）

---

## 若落在 Tepeu，落哪

| 落点 | 说明 |
|------|------|
| AuditSink / ledger 导出纪律 | 企业导出字段白名单、禁 prompt/secret 默认外泄 |
| Secret / 发行 | Key Vault 类场外；compose 不读密钥 |
| gap 诚实度 | 管线绿 ≠ 作业平台齐 |

运行时门禁主线仍见 [agent-runtime-security-series.md](./agent-runtime-security-series.md)。

**不**因本文新增 ADR。
