# legacy/ — v1.0 参考实现（只读）

本目录存放从仓库根挪入的旧版业务代码，供 **develop 重写**对照。

## 内容

| 路径 | 原位置 |
|------|--------|
| `backend/` | 根目录 `backend/`（Spring Boot v1） |
| `frontend/` | 根目录 `frontend/`（Vite + React v1） |
| `experiments/` | ATE 等实验 |
| `scripts/` | 杂脚本 |
| `os-9/` | develop 九模块标本（`os/` `host/` 源码，只读） |

## 规则

1. **只读参考**：禁止在此目录加新功能或修产品行为。
2. **可运行标本**：若需跑旧版，优先 `git checkout main`；确需在本目录运行时，自行 `cd legacy/backend` / `legacy/frontend`。
3. **迁移完成即删**：新内核/积木接管某能力后，删除对应 legacy 片段；全部迁完可移除整个 `legacy/`。
4. **权威冻结线**：远程 `main` @ 审计收口 + tag `v1.0.0`（发布点略早于审计提交）。

架构边界见 `memory/decisions-log.md` → **ADR-016**。
