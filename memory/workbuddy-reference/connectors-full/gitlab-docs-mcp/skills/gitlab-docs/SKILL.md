---
name: gitlab-docs
description: 检索 GitLab 官方文档与 Release Notes。当用户询问 GitLab / GitLab Runner / Gitaly / Omnibus 等配置、API 用法、报错排查、版本变更（CHANGELOG / upgrade notes）时使用本技能。先 search 再 get_document；查版本变更用 search_release_notes；不确定版本是否存在时先 list_indexed_versions。
---

# GitLab 文档检索

通过 MCP 服务器 `gitlab-docs` 检索本地索引的 GitLab 官方文档（含 gitlab、gitlab-runner、gitaly、omnibus-gitlab、gitlab-shell、gitlab-environment-toolkit、ai-gateway、congregate、performance 等仓库）。

## 工具清单

| 工具 | 用途 |
|---|---|
| `search_docs` | 全文搜索文档正文，支持按仓库与版本过滤 |
| `search_release_notes` | 只搜 CHANGELOG.md / RELEASE.md / CHANGES.md 及 release_notes/、releases/、changelogs/ 目录 |
| `get_document` | 读取指定文档的完整内容或指定行范围 |
| `list_indexed_versions` | 列出某仓库已同步（可查询）的全部版本 |

## 使用规则

1. **先搜索，后读取**。`search_docs` 返回的是带 `repository` / `version` / `path` / `start_line` / `end_line` 的片段；需要上下文时用这些字段调用 `get_document`（可传 `start_line` / `max_lines`），不要凭片段臆断。
2. **"版本 X 有什么变化 / 升级注意 / breaking changes" 一律用 `search_release_notes`**，不要用 `search_docs`。HEAD 检出覆盖完整发布历史：新版本在根 `CHANGELOG.md`，旧版本在 `changelogs/archive-*.md`。直接搜版本号字符串（如 "GitLab 15.1.0 变更"）通常即可，**不必带 repository**，以便兄弟仓库（gitaly、gitlab-runner、omnibus-gitlab）一起匹配。
3. **`search_release_notes` 的范围是严格的**：生效范围内无匹配就返回空，不会跨版本回退。若对显式 `version` 查询返回空，先调 `list_indexed_versions` 确认该版本是否已索引，再决定换版本或不带 version。
4. **不带 version 时默认搜索各仓库 HEAD（默认分支）**，结果反映最新文档。要查历史行为必须显式传 `version`（形如 `v17.1.5-ee`）。
5. **`limit` 取 1–20**，默认 5。宽泛问题可取 8–10；已定位到具体关键词时 3–5 即可。
6. **回答时引用出处**：给出 `repository`、`version`、`path` 与行号区间，便于用户核对原文。

## 典型示例

- 「`.gitlab-ci.yml` 里 `rules` 和 `only/except` 的关系？」→ `search_docs(query="rules only except", repository="gitlab")`
- 「GitLab 17.11 的 runner cache 配置？」→ `search_docs(query="cache config", repository="gitlab-runner", version="v17.11.0-ee")`（版本不存在时先 `list_indexed_versions`）
- 「升级到 16.11 有什么 breaking changes？」→ `search_release_notes(query="16.11 breaking changes", repository="gitlab")`
- 「omnibus 的 PostgreSQL 13 升级说明」→ `search_release_notes(query="PostgreSQL 13 upgrade", repository="omnibus-gitlab")`
- 「这个报错 `Element ... has invalid type` 怎么解决？」→ `search_docs(query="Element has invalid type")`，把报错关键字直接作为 query。
