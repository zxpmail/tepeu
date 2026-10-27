---
name: creatok-assets
display_name: CreatOK 素材库
display_name_en: CreatOK Assets
description: Import, upload, browse, inspect, delete, restore, and permanently purge CreatOK Assets with organization scoping and explicit deletion previews.
description_zh: 在组织权限范围内导入、上传、浏览、查看、删除、恢复和永久清理 CreatOK 素材。
description_en: Import, upload, browse, inspect, delete, restore, and permanently purge CreatOK Assets with organization scoping and explicit deletion previews.
category: assets
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_asset_import_from_url, creatok_asset_upload_prepare, creatok_asset_upload_finalize, creatok_asset_get, creatok_assets_summary_get, creatok_assets_list, creatok_asset_members_list, creatok_asset_delete, creatok_assets_delete_preview, creatok_assets_delete_execute, creatok_recycle_bin_list, creatok_recycle_bin_restore, creatok_recycle_bin_delete_preview, creatok_recycle_bin_delete
---

# CreatOK Asset management

Read and apply [`./references/user-experience.md`](./references/user-experience.md) before browsing, presenting media, confirming deletion, or reporting storage changes.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools above. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, direct endpoints, or a local CLI as fallback. The only local-execution exception is the packaged helper in [`./references/local-media-upload.md`](./references/local-media-upload.md), which may inspect required file metadata and PUT one explicit local attachment to the exact HTTPS URL returned by `creatok_asset_upload_prepare`; it must not call CreatOK APIs or read credentials. Never request or read API keys, tokens, `Authorization` headers, connector configuration, credential files, or environment variables including `CREATOK_API_KEY`; never display, validate, or transmit them. If secret-like content appears unexpectedly, suppress it and report only the stable error code and `request_id`. For `WORKSPACE_REQUIRED`, ask the user to reconnect the connector and pick an organization workspace during authorization; never ask for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every execution step must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

## Asset boundaries

Call `creatok_capabilities_get` for current upload/import media types and size limits. Ordinary Asset mutation covers `user_upload`, `ai_tasks`, and `sora_generations`. Avatar group media must use Avatar tools. Product-library media is visible only in aggregate storage accounting and is not manageable through the current MCP; never substitute ordinary Asset or TikTok catalog tools for it.

Read `upload_limits.asset` for staged uploads and `upload_limits.url_import` for remote imports on every new media intent. If `creatok_asset_upload_finalize` includes optional model context, use only a current model ID returned in `models.video.models`; never copy or invent a finalize model.

Treat every `asset_uid`, cursor, preview ID, operation ID, object key, and resource URL as opaque. Never construct or decode them. Internal object keys may appear only in the staged upload prepare/finalize exchange and must not be exposed as reusable media identifiers.

## Import and upload

- Before importing or uploading, require an explicitly selected attachment/public URL and the media type needed by current capabilities. If the user only says “upload/import media” or merely attaches a file without saying to use it, ask which visible file should be added and wait; do not call prepare/import or inspect unrelated library content. An explicit request to upload a named attachment is sufficient authorization for that exact upload and must not trigger a redundant confirmation.
- Preferred remote attachment path: call `creatok_asset_import_from_url` with the server-accessible HTTPS URL, media type, optional filename/expected byte size/SHA-256, and one stable idempotency key. Never forward WorkBuddy identity or authentication headers. Header-protected or server-inaccessible remote URLs are unsupported.
- When an explicit WorkBuddy image/video attachment is available only as a local absolute path, follow [`./references/local-media-upload.md`](./references/local-media-upload.md): inspect only required file metadata with the packaged helper, call prepare with the exact filename/MIME/size, PUT exactly those bytes through the same helper, then finalize with the returned staged object key and a stable idempotency key. Never replace the helper with curl, a browser, a general HTTP tool, or an improvised script. Local audio is not supported by staged upload.
- Do not send local absolute paths or Base64 media through MCP JSON.

Signed Asset resource links are temporary — roughly a day — so say that when handing one over, and call `creatok_asset_get` or list again to refresh an expired one. Give every Asset its link as a Markdown link with a short visible label; never print a bare URL as visible text, never persist or reconstruct one. Present each requested Asset set through exactly one WorkBuddy sidebar using one native resource-presentation action; do not open one sidebar per Asset.

## Browse and delete

Before browsing, resolve the user-visible category or explicit filters required for the next list. Use summary/list/member tools only after that scope is known; do not open a default category as a substitute for asking. Do not infer access from an ID that was not returned to this actor.

For a broad “show my Asset Library” request, use the currently supported category menu and order in [`./references/user-experience.md`](./references/user-experience.md). Within this Skill, `AI 生成` maps to `source: ["ai_tasks", "sora_generations"]`, `上传资产` maps to `source: ["user_upload"]`, and `回收站` uses the ordinary-Asset recycle tools. Route `数字人` to the installed Avatar Skill. Product-library management is currently unsupported; state that limitation and stop instead of querying ordinary Assets or TikTok products as a substitute. MCP cannot move Avatars or Avatar media into recycle, nor list or restore them from recycle, so never imply that a broad recycle result includes them. If the user already supplied a supported category, media type, source, date range, member, or cleanup criterion, use it directly without another menu.

Call `creatok_assets_summary_get` before a broad list and summarize matched count, known bytes with any unknown-size qualification, date span, and storage context when useful. If the result count is zero, say so and offer to widen the filter. For 1–20 results, list immediately. For more than 20, offer a useful narrowing choice before paging unless the user explicitly asked to see all.

Present list results as compact numbered choices using visible media type, title/filename when available, size, and age/date, and put a link to the item on every row — `播放` for video and audio, `查看` for an image, or the equivalent in the user's language. A listing the user cannot open is not an answer to “看看我的素材”. Put the corresponding previews into one numbered WorkBuddy sidebar gallery in the same visible order; if WorkBuddy cannot group the current set in one sidebar, keep the numbered text list with its links and show only the item the user selects, replacing the current sidebar rather than opening another one. Keep every `asset_uid`, cursor, object key, and source-system value private. Fetch another page only when the user asks, and say how many results remain when known.

For an exact small set, map the user's selected numbers to the latest still-current results and call `creatok_asset_delete` with an explicit `mode`. Every removal requires confirmation, including recycle. Name the visible targets and size when known. Explain that recycle is recoverable under the returned retention policy and does not free space immediately; explain that permanent deletion is irreversible and how much space it releases. Never choose the mode for the user.

For Chinese replies, use `素材`, `存储空间`, `已用空间`, `回收站`, `移入回收站`, `永久删除`, `立即释放`, and `到期释放`. Ask for one direct confirmation after the exact target/count/bytes and consequence are visible; never expose deletion flags or preview handles.

For batch or filtered deletion:

1. Call `creatok_assets_delete_preview` with exactly one valid selector and the intended mode.
2. Show the user the exact matched count, known/unknown bytes, release timing, mode, and preview expiry.
3. Obtain explicit confirmation.
4. Call `creatok_assets_delete_execute` with the returned `preview_id`, exact `expected_count`, and one stable idempotency key. If the set drifted, preview again.

For recycle-bin purge, use `creatok_recycle_bin_delete_preview`, show count/bytes/irreversibility, obtain one explicit confirmation of that exact preview, then call `creatok_recycle_bin_delete` with the same preview, expected count, confirmation flag, and stable idempotency key. Restore is non-destructive, needs no confirmation, and uses its own stable idempotency key.

Moving to recycle does not immediately free storage; permanent purge does. Never claim space was released unless the tool result says so.

For a general “free some space” request, first show current usage and the largest manageable categories instead of asking the user what consumes space. Raise meaningful recycle-bin usage before active Assets, then propose two or three concrete targets. Once the user chooses a target, preview the exact selector, confirm its current count/bytes and release timing, execute only the approved set, and refresh the summary so the final answer states the actual space released and still pending release. Never download media during a cleanup flow.

On `RESULT_UNKNOWN`, reuse the same idempotency key and inspect the affected Asset/recycle state before any new mutation. Keep `operation_id` internal for recovery; after the user-facing reason and next action, show `request_id` as a short diagnostic reference.
