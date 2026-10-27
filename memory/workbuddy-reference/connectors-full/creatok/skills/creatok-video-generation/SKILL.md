---
name: creatok-video-generation
display_name: CreatOK 视频生成
display_name_en: CreatOK Video Generation
description: Prepare prompts and generate CreatOK videos from text, images, videos, first-frame or first-and-end-frame inputs, or Avatars with guided choices, credit confirmation, local delivery, and task recovery.
description_zh: 准备提示词并使用文字、图片、视频、首帧/首尾帧或数字人生成 CreatOK 视频，提供引导式选择、积分确认、本地交付和任务恢复。
description_en: Prepare prompts and generate CreatOK videos from text, images, videos, first-frame or first-and-end-frame inputs, or Avatars with guided choices, credit confirmation, local delivery, and task recovery.
category: video
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_asset_import_from_url, creatok_asset_upload_prepare, creatok_asset_upload_finalize, creatok_asset_get, creatok_assets_list, creatok_avatars_list, creatok_avatar_get, creatok_avatar_register, creatok_credits_estimate, creatok_video_prepare, creatok_video_generate, creatok_task_get
---

# CreatOK video generation

Use this Skill for text-to-video and supported reference-media or Avatar video generation. Read and apply [`./references/user-experience.md`](./references/user-experience.md) before asking questions, listing choices, confirming cost, or presenting results.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools listed above for CreatOK operations. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, direct endpoint calls, or local CLI commands as a fallback. Local execution has exactly two narrow exceptions: the packaged helper in [`./references/local-media-upload.md`](./references/local-media-upload.md) may upload one explicit local image/video attachment to the exact prepared HTTPS PUT URL, and the post-success delivery procedure below may fetch exact HTTPS result URLs. Neither path may read connector state or carry an `Authorization` header or credential. Never request or read API keys, tokens, `Authorization` headers, connector configuration, token-schema files, credential files, or environment variables including `CREATOK_API_KEY`. Never print, summarize, transform, validate, or transmit such values. If secret-like content appears unexpectedly, do not repeat it; report only the stable error code and `request_id` and tell the user to rotate the key in CreatOK/WorkBuddy.

`WORKSPACE_REQUIRED` means the connection is not bound to an organization workspace. Tell the user to reconnect the connector and pick an organization workspace during authorization; never ask them for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every CreatOK business operation must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback. Local execution is allowed only for the packaged local-upload helper and delivery of already-successful CreatOK media as specified here; it must not invoke CreatOK APIs, MCP, or generation/processing capabilities.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

For the video frame workflow, the only valid choices are first frame alone or first frame plus end frame; an end frame can never be used alone. In every user-visible Chinese reply, call this `首帧/首尾帧`, never `首/尾帧` or `首帧/尾帧`. In English, use “first-frame or first-and-end-frame”, never “first/end-frame”. This wording rule does not rename the internal `first_frame_asset_uid` and `end_frame_asset_uid` tool arguments.

## Seedance prompt packaging (on demand)

Before entering the generation workflow, if the user explicitly requests Seedance prompt packaging, conversion, compression or prompt diagnosis, read [`./references/seedance2.md`](./references/seedance2.md). This reference owns the prompt-only step and return to generation. A normal Seedance generation request does not activate it.

## Workflow

1. Call `creatok_capabilities_get` for every new generation intent. Its current models, defaults, media combinations, prompt limits, upload limits, and prices are authoritative. Do not cache or invent enums.
   For the selected entry in `models.video.models`, obey `limits.max_prompt_length`, `max_reference_images`, `max_reference_videos`, `max_reference_media_items`, duration/orientation/definition choices, and the advertised frame/reference media constraints. Prompt length is measured in Unicode code points, not UTF-16 code units.
   Resolve the workflow through `services.execution_tools`: use `estimate`, `submit.video_generation`, and `status`. If WorkBuddy does not expose any of those named CreatOK tools, stop and report that compatibility error instead of substituting a built-in or third-party capability.
2. Treat every non-empty prompt supplied with the generation request as final, even when it is only a few words, fragmentary, vague, or low-detail. Carry it character-for-character through prepare, estimate, confirmation, and submission; never rewrite, expand, translate, correct, or optimize it. If prompt text is completely absent, ask for it. You may briefly mention the separate CreatOK prompt-optimization capability, but do not invoke it or delay generation unless the user explicitly asks. Ask at most one short question about a genuinely required missing choice; use current defaults only for optional settings that do not replace a material user decision.
3. Resolve the media-source choice before any Asset query, attachment import/upload, or sidebar action. For image-to-video, reference-media, or frame-based generation with no exact selected media/source, ask in Chinese: `图片准备从哪里选择？1. 上传新的图片 2. 从 CreatOK 素材库选择 3. 上传图片和素材库图片都使用` and say that no generation starts until the media and later price confirmation are complete. Offer option 3 only when current capabilities allow enough inputs. If attachments exist but the user did not explicitly select them, ask whether to use only those attachments, only the Asset Library, or both. If the user explicitly says to use a named attachment or visible Asset, accept that source choice without asking again. Never default to `AI 生成`, never call `creatok_assets_list`, and never open a sidebar before the source choice.
4. After the user chooses the Asset Library, ask `AI 生成` / `上传资产` / `两类都查看` if they did not already name a category; only then call `creatok_assets_list` with the corresponding image/video filters. Present compact numbered choices and put their previews into one WorkBuddy sidebar gallery in the same order; keep Asset UIDs private and wait for the exact item choice. For an explicitly selected WorkBuddy attachment or public media URL, call `creatok_asset_import_from_url` when its HTTPS URL is server-accessible without extra headers. When that explicit image/video attachment is exposed only as a local absolute path, follow [`./references/local-media-upload.md`](./references/local-media-upload.md), then use the formal Asset UID returned by finalize. Never pass attachment authentication headers or WorkBuddy identity data. Header-protected remote URLs and local audio are unsupported.
5. Use `creatok_avatars_list`/`creatok_avatar_get` only when an Avatar is requested or when a real-person reference needs an identity-preserving path. Present an Avatar-only numbered list with visible name and description, wait for selection, then fetch that Avatar and present a second numbered list containing only its media previews and visible media type. Put those previews into exactly one WorkBuddy sidebar through one native resource-presentation action and replace any earlier media sidebar. Keep UIDs and provider readiness private. Do not silently choose an Avatar or nested asset. Never construct Asset or Avatar identifiers. After the final model and Avatar asset are selected, call `creatok_avatar_register` automatically with one stable idempotency key only when the returned model readiness requires it; query the same Avatar until ready, and mention preparation to the user only if it fails.
6. Call `creatok_video_prepare` only after the prompt, media-source mode, and exact media choices are resolved. Treat its normalized options, readiness, and `estimate_input` as authoritative. Reference-media mode may be mutually exclusive with both first-frame and first-and-end-frame modes; never bypass a tool validation error.
7. Call `creatok_credits_estimate` using `{service: "video_generation", options: <the prepared options>}`.
8. In the user's language, show a compact field-per-line confirmation containing the approved prompt summary, model `label`, orientation when applicable, duration, definition, reference/frame choices, Avatar name and selected media number or “not used”, single-shot versus multi-segment plan, estimated credits, remaining credits, and estimate expiry. Mention the settings the user can still adjust and ask for one explicit confirmation. Never show model IDs, Asset UIDs, estimate IDs, or provider fields.
   For Chinese confirmations, use applicable labels from: `提示词`, `模型`, `画面比例`, `时长`, `清晰度`, `参考素材`, `首帧/首尾帧`, `数字人`, `生成方式`, `预计消耗`, `剩余积分`, `报价有效期`. End with: `参数可以调整，如需修改请直接告诉我；不需要修改请回复“确认”开始生成。`
9. Only after clear confirmation, call `creatok_video_generate` with exactly the confirmed options, `cost_confirmation: {estimate_id, max_credits}`, and one stable `idempotency_key` for this user intent.
10. Keep the same idempotency key after a timeout or uncertain response. Never create a new key for a retry. Changed parameters require a new estimate, a new confirmation, and a new key.
11. On submission, preserve `task_id` and poll `creatok_task_get` according to `retry_after_seconds` until `succeeded`, `failed`, or `canceled`. Queued/running tasks are not a reason to resubmit.
12. On success, perform the mandatory local delivery procedure below for every returned video rather than replying with a remote resource link.

If the requested duration exceeds the selected model's current limit, explain the limitation and offer a concrete segmented plan before confirmation. Never silently shorten the content or claim the generated clips will be stitched automatically.

For Seedance-family generation, preserve every supplied prompt unchanged by default. Only when the user explicitly asks for Seedance prompt packaging may you turn their decided content into a faithful executable prompt, use `@图片 N` references in the same order as selected images, show the completed prompt in its own turn, and wait for a later instruction before estimating or generating. Do not infer packaging permission from a short prompt and do not add new hooks, dialogue, shots, transitions, music, or creative decisions.

All continuation handles must come from the connected MCP result. When structured output is unavailable, read the serialized JSON in the first text result. Never reconstruct IDs or URLs.

## Local delivery of successful videos

When `creatok_task_get` returns `status: "succeeded"` and a non-empty `result_assets` array:

1. Run `__SKILL_DIR__/scripts/download_creatok_results.py` through WorkBuddy local execution with the current workspace as its working directory. Pass the exact `task_id` through `--task-id` and send the complete, unmodified `result_assets` JSON array on stdin. Use `python3`, or `python` when that is the available Python 3 command. If the packaged helper is missing, report an incomplete Skill installation; do not create a substitute downloader.
2. Accept only the exact HTTPS `media_url`/`resource_link.uri` returned by CreatOK. Never construct the URL, add request headers, use `Authorization`, read credentials, or use local execution to call a CreatOK API/MCP endpoint. The helper must save only under `creatok_output/<task_id>/`, validate media and byte size, and atomically finalize non-empty files.
3. Call WorkBuddy `present_files` exactly once with all successfully downloaded absolute local paths so every returned video appears in one ordered sidebar. Reuse or replace the active sidebar; never call `present_files` once per video or duplicate the videos inline in chat. In the final response, use the user's language, identify results by visible order, give the actual local paths and a Markdown link per result, and identify any failed result numbers; keep technical handles out of the reply.
4. If the URL expired, call `creatok_task_get` again for the same task and retry only the failed downloads with refreshed URLs. A download or display failure must never cause `creatok_video_generate` or any other paid task to be submitted again. If there is no writable workspace, local execution/Python is unavailable, or `present_files` fails, report that limitation and the completed `task_id`; never claim local delivery succeeded.

## Recovery

- `CONFIRMATION_REQUIRED`: obtain a new estimate and ask again.
- `INSUFFICIENT_CREDITS`: do not submit; explain that the organization needs credits.
- `TASK_NOT_READY`: wait and query the known task.
- `RESULT_UNKNOWN`: do not change the idempotency key. Query the known `task_id` or `asset_uid`; otherwise report `operation_id` and `request_id` for support.
- `PERMISSION_DENIED`/`UNAUTHENTICATED`/`WORKSPACE_REQUIRED`: ask the user to reconnect without requesting any secret.

Always preserve `request_id`; after a plain-language reason and next action, show it as a short diagnostic reference.

For an explicit general prompt-optimization request, hand off to the `creatok` entry and its direct `creatok_prompt_optimize` workflow, carrying the supplied prompt and reference choices. Do not require a separate optimization Skill.
