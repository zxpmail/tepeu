---
name: creatok-avatar
display_name: CreatOK 数字人
display_name_en: CreatOK Avatars
description: Create and manage CreatOK Avatars from formal organization Assets, including model registration and permanent deletion.
description_zh: 使用正式组织素材创建和管理 CreatOK 数字人，并安全处理模型注册与永久删除。
description_en: Create and manage CreatOK Avatars from formal organization Assets, including model registration and permanent deletion.
category: assets
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_asset_import_from_url, creatok_asset_upload_prepare, creatok_asset_upload_finalize, creatok_asset_get, creatok_assets_list, creatok_avatars_list, creatok_avatar_get, creatok_avatar_create, creatok_avatar_register, creatok_avatar_delete
---

# CreatOK Avatars

Read and apply [`./references/user-experience.md`](./references/user-experience.md) before listing choices, presenting media, creating or deleting records, or handing an Avatar to video generation.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools above. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, direct endpoints, or a local CLI as fallback. The only local-execution exception is the packaged helper in [`./references/local-media-upload.md`](./references/local-media-upload.md), which may upload one explicit local image/video attachment to the exact HTTPS URL returned by `creatok_asset_upload_prepare`; it must not call CreatOK APIs or read credentials. Never request or read API keys, tokens, `Authorization` headers, connector configuration, credential files, or environment variables including `CREATOK_API_KEY`; never display or transmit them. If secret-like content appears unexpectedly, suppress it and report only the stable error code and `request_id`. For `WORKSPACE_REQUIRED`, ask the user to reconnect the connector and pick an organization workspace during authorization; never ask for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every execution step must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

Call `creatok_capabilities_get` first. Its Avatar media composition, file sizes/types, quota, extra-slot rules, and models are authoritative.

Use current capability and tool-schema constraints for names, descriptions, composition and quota; do not duplicate their field definitions here.

## Media preparation

Avatar creation accepts formal organization `asset_uid` values, never raw local paths or internal object keys. Before an Asset query, attachment import/upload, or sidebar action, resolve whether the required media will be uploaded, selected from the CreatOK Asset Library, or combined from both when current composition limits allow it. If attachments exist but were not explicitly selected, ask whether to use them; do not infer consent from attachment presence. Never default to `AI 生成`. After the user chooses the Asset Library, ask `AI 生成` / `上传资产` / `两类都查看` when no category was specified; only then call `creatok_assets_list`, present visible numbered choices, and put the matching previews into one WorkBuddy sidebar gallery while keeping UIDs private. Otherwise use an explicitly selected existing Asset, call `creatok_asset_import_from_url` for an explicitly selected server-accessible HTTPS attachment/public URL, or follow [`./references/local-media-upload.md`](./references/local-media-upload.md) when that explicit image/video attachment is exposed only as a local absolute path. Continue only with the formal Asset UID returned by import/finalize. The URL-import tool also supports Avatar WAV/MP3 audio under its advertised limits; staged local audio is unsupported. Never forward WorkBuddy authentication or identity headers. Header-protected URLs and Base64 payloads are unsupported.

## Avatars

Use `creatok_avatars_list`/`creatok_avatar_get` to inspect current quota, composition, status, and model readiness. Do not claim an Avatar is ready unless the returned model status says so.

For discovery, first present an Avatar-only numbered list containing the visible name and description. Wait for the user to choose one, then fetch fresh detail and present a second numbered gallery containing only the returned image/video/audio previews and visible media type. Put that gallery into exactly one WorkBuddy sidebar through one native resource-presentation action without printing URLs; replace any earlier media sidebar instead of opening another one. Keep Avatar/Asset UIDs, object keys, readiness flags, provider state, and registration details private. Do not silently select an Avatar or nested media item unless the user already identified it unambiguously by visible name.

For Chinese replies, call an Avatar `数字人`, an Avatar asset `数字人素材`, and a reference image `参考图素材`. Use `可选数字人` and `可选素材` as selection headings, then ask for the visible number or name. Never translate Avatar as `头像` in this product workflow.

For create, collect only the missing name, optional description, media-source choice, and exact media required under current capabilities. Do not call `creatok_avatar_create` until those material choices are resolved. Present attachments by visible filename/preview while keeping imported formal Asset UIDs internal. Use one stable idempotency key. Do not set the extra-slot charge confirmation unless the server reports that the quota is exhausted, shows the exact extra-slot cost, and the user explicitly confirms that amount. On confirmation, repeat the same intended create with `confirm_extra_charge: true` and the stable key. Never infer charge consent from a media-generation confirmation, and report creation only after the tool confirms it.

Register with an advertised model only when the selected video model and chosen Avatar asset require it. Treat registration as automatic preparation after asset selection: do not ask for separate permission or expose provider registration terminology unless it fails. Registration may take time; on pending/timeout keep the same idempotency key and inspect the Avatar again instead of starting another registration. `creatok_video_prepare` is read-only and never registers an Avatar implicitly, so complete this step before preparing Avatar-based video generation.

Delete only an exact Avatar UID or nested Asset UID privately mapped from the latest numbered display. Read `avatar_limits.delete_modes` and `avatar_limits.recycle_supported`; Avatar deletion through MCP is permanent-only: never offer, request, or submit recycle mode, and never imply that an Avatar or Avatar media can be moved into or restored from the recycle bin. Identify whether the visible target is the whole Avatar or one numbered media item, explain that deletion is irreversible and releases storage immediately, and require explicit confirmation before sending `mode: "permanent"` with `confirm_permanent_delete: true`. Use one stable idempotency key and preserve returned storage-release semantics. If the last required media item cannot be removed, offer permanent deletion of the whole Avatar instead; never retry another target automatically.

For `RESULT_UNKNOWN`, reuse the same idempotency key and inspect the exact Avatar state before any new mutation. Keep `operation_id` and UIDs internal for recovery; after the user-facing reason and next action, show `request_id` as a short diagnostic reference. Never expose internal object keys or provider IDs.
