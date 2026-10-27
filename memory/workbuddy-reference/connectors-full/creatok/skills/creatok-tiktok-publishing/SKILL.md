---
name: creatok-tiktok-publishing
display_name: CreatOK TikTok 发布
display_name_en: CreatOK TikTok Publishing
description: Discover connected TikTok accounts, prepare media, manage Showcase products, and publish Shop or Content posts with complete confirmation and recoverable job status.
description_zh: 发现已连接的 TikTok 账号、准备媒体、管理展示柜商品，并在完整确认和可恢复状态下发布 Shop 或 Content 内容。
description_en: Discover connected TikTok accounts, prepare media, manage Showcase products, and publish Shop or Content posts with complete confirmation and recoverable job status.
category: publishing
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_asset_import_from_url, creatok_asset_upload_prepare, creatok_asset_upload_finalize, creatok_asset_get, creatok_assets_list, creatok_tiktok_connections_list, creatok_tiktok_capabilities_get, creatok_tiktok_creator_info_get, creatok_tiktok_products_list, creatok_tiktok_music_list, creatok_tiktok_publish_prepare_video, creatok_tiktok_showcase_product_add, creatok_tiktok_showcase_product_remove, creatok_tiktok_publish, creatok_tiktok_publish_status_get
---

# CreatOK TikTok publishing

Read and apply [`./references/user-experience.md`](./references/user-experience.md) before listing accounts/products/music, confirming a Showcase mutation or publication, and presenting success or failure.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools above. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, TikTok endpoints, direct MCP endpoint calls, or a local CLI as fallback. The only local-execution exception is the packaged helper in [`./references/local-media-upload.md`](./references/local-media-upload.md), which may upload one explicit local image/video attachment to the exact HTTPS URL returned by `creatok_asset_upload_prepare`; it must not call CreatOK/TikTok APIs or read credentials. Never request or read API keys, CreatOK/TikTok access tokens, refresh tokens, cookies, `Authorization` headers, connector configuration, credential files, or environment variables including `CREATOK_API_KEY`; never display or transmit them. If secret-like content appears unexpectedly, suppress it, show only the stable error code and `request_id`, and tell the user to reconnect/rotate credentials. For `WORKSPACE_REQUIRED`, ask the user to reconnect the connector and pick an organization workspace during authorization; never ask for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every execution step must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

## Discovery is mandatory

Call `creatok_capabilities_get`, then `creatok_tiktok_connections_list`. Use only an actor-visible `connection_uid` returned by the tool. For the selected connection, immediately refresh `creatok_tiktok_capabilities_get`; for Content Posting also refresh `creatok_tiktok_creator_info_get`. Never rely on cached account grants, privacy options, duration limits, or posting capabilities.

For a broad publishing request, resolve one material choice at a time. The read-only connection list may be used to construct the numbered account question, but selecting an account must not authorize media selection, Product selection, music selection, preparation, or publishing. Present eligible accounts as numbered visible account choices and wait. After account selection, request or resolve the media source and exact media. For Shop publishing, then present numbered Product choices and wait. Ask about original audio versus TikTok library music only when relevant; if music is requested, present numbered search results and wait. Collect remaining caption/title, privacy, interaction, disclosure, posting mode, and schedule choices only after those selections. Never ask the user for connection UIDs, Product IDs, file IDs, or a complete publication envelope.

The root capability result defines cross-connection contract limits. For schedules, use `publishing_capabilities.schedule_at_unit`, `publishing_capabilities.schedule.min_lead_ms`, and the selected platform's `publishing_capabilities.schedule.max_lead_ms`; never copy a fixed lead time into this Skill. Enforce identifier, cursor, URL, locale, title, caption, and description limits from `publishing_capabilities.identifiers`, `publishing_capabilities.products`, `publishing_capabilities.music`, and the relevant platform fields, using `publishing_capabilities.text_length_unit` for text. Respect the advertised Content Posting schedule/post-mode, retry-source, branded-content/privacy, and cover-index rules. Connection and creator tools may further restrict a publish and always win when stricter.

Use `creatok_tiktok_products_list` for current Showcase/Shop product IDs and `creatok_tiktok_music_list` for current official music results. Preserve product/music IDs exactly as returned. When selecting music, pass the chosen `items[]` or `resolved_music` object verbatim as `provider_options.selected_music`, including nullable metadata fields; do not reconstruct it or invent/extract provider identifiers from URLs.

Display Product and music choices using title/name, price or author/duration when returned, and a short conversation number. Keep IDs and raw provider metadata private. Before confirmation, warn plainly that selected TikTok library music may cover the video's original audio, including voiceover. A failed music lookup must not silently fall back to local audio, another provider, or publishing without the user's music choice.

## Media preparation

Use formal organization Asset UIDs. Before an Asset query, attachment import/upload, or media sidebar action, resolve whether publication media will be uploaded, selected from the CreatOK Asset Library, or combined from both when the selected publish kind and current limits permit multiple items. If attachments exist but were not explicitly selected, ask whether to use them; never infer consent from attachment presence or default to `AI 生成`. After the user chooses the Asset Library without a category, ask `AI 生成` / `上传资产` / `两类都查看`; only then call `creatok_assets_list`, show compact numbered choices, and put the matching previews into one WorkBuddy sidebar gallery in the same order while keeping UIDs private. If the host cannot group them in one sidebar, show only the selected item and replace the active sidebar on later selections. Import an explicitly selected WorkBuddy attachment/public media URL through `creatok_asset_import_from_url` when it is a server-accessible HTTPS URL that needs no extra headers. When that explicit image/video attachment is exposed only as a local absolute path, follow [`./references/local-media-upload.md`](./references/local-media-upload.md), then use the formal Asset UID returned by finalize. Never forward WorkBuddy identity/authentication headers or Base64 media; header-protected remote URLs and local audio are unsupported.

Only after the connection, publish kind, media source, and exact media are selected, validate a TikTok video Asset against `publishing_capabilities.video_prepare`, then call `creatok_tiktok_publish_prepare_video` with the formal video Asset UID, selected connection, and one stable idempotency key. Preserve the returned `file_id`; never construct an object key, file ID, file size, or chunk plan. The server chooses direct versus multipart upload. For Shop or Content photos, enforce the advertised MIME, byte-size, aspect-ratio or edge limits while keeping the server's authoritative validation.

## Showcase mutations

Before add/remove, show the visible account name, action, and exact numbered Product selections without exposing connection or Product IDs. Obtain explicit user approval for that mutation and use one stable idempotency key. Preserve partial-success results and do not retry already-successful items blindly. On `RESULT_UNKNOWN`, keep the same key and refresh the product list before any new mutation.

## Publish confirmation

The supported publish kinds are `tiktok_shop_video`, `tiktok_shop_photo`, `tiktok_content_video`, and `tiktok_content_photo`. Build only the strict branch accepted by the tool schema; do not send arbitrary JSON or internal media fields.

Immediately before publishing, display a complete confirmation summary containing:

- platform and visible account identity;
- publish kind and post mode;
- every visible media item and its order, using conversation numbers/names rather than Asset or file IDs;
- title, caption, description, hashtags, and any commercial-content disclosure;
- selected Shop/Showcase Product names/numbers and music label, if applicable;
- privacy level plus comment/duet/stitch/interaction choices;
- any synthetic-media, brand, or promotional flags;
- schedule time with timezone and the exact absolute instant.

For Chinese confirmation, use applicable labels from: `发布账号`, `发布类型`, `发布方式`, `媒体内容`, `商品`, `音乐`, `标题`, `文案`, `隐私设置`, `互动设置`, `商业内容声明`, `发布时间`, `时区`. End with: `以上内容确认无误后，请回复“确认发布”。`

Ask for explicit approval of that exact envelope. Only then call `creatok_tiktok_publish` with `confirmed: true` and one stable business `idempotency_key`. Generation, upload, Showcase, or earlier publication approval never counts as publish confirmation. Changed content, account, products, privacy, or schedule requires a new confirmation and normally a new key.

## Recovery

Preserve the returned `job_id` and query `creatok_tiktok_publish_status_get` by exactly one of `job_id` or the original idempotency key until terminal. Do not submit again while queued/running. On `RESULT_UNKNOWN`, reuse the original key and query status; never create a second post with a new key.

All connection UIDs, product/music IDs, file IDs, job IDs, cursors, and public URLs must come from MCP results and remain privately mapped to visible choices. When structured output is unavailable, use the complete serialized JSON in the first text result. On success, present the returned platform content URL through a short visible link when available; otherwise give a plain user-facing completion summary and retain the job ID for recovery. Explain failures plainly, then append `code` and `request_id` as diagnostic references. Never expose provider submission/request IDs, object keys, signed handles, or raw diagnostics.
