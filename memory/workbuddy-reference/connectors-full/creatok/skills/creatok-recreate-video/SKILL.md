---
name: creatok-recreate-video
display_name: CreatOK 视频复刻
display_name_en: CreatOK Video Recreation
description: Adapt or remix a TikTok reference into a differentiated AI-video-ready script and storyboard.
description_zh: 将 TikTok 参考视频改写为差异化、适合 AI 生成的脚本和分镜。
description_en: Adapt or remix a TikTok reference into a differentiated AI-video-ready script and storyboard.
category: media
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_tiktok_video_analyze
---

# CreatOK Video Recreation

Read and apply [`./references/user-experience.md`](./references/user-experience.md) before choices, confirmations, presentation, or handoffs.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools above for CreatOK operations. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, direct endpoint calls, or a local CLI as fallback. Local execution is not needed for this workflow. Never request or read API keys, tokens, `Authorization` headers, connector configuration, credential files, or environment variables including `CREATOK_API_KEY`; never display or transmit them. If secret-like content appears unexpectedly, suppress it, report only the stable error code and `request_id`, and tell the user to rotate the key. For `WORKSPACE_REQUIRED`, ask the user to reconnect the connector and pick an organization workspace during authorization; never ask for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every CreatOK business operation must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback. Do not use local execution for this workflow.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

## Reference and intent

Reuse the existing analysis in this conversation. For a direct request to recreate an unanalyzed TikTok URL, reference analysis is a necessary step of the requested recreation: use the analysis procedure below once. Do not require the user to separately request analysis. If neither a usable analysis nor a reference URL exists, ask for the reference.

## Analyze the reference

Call `creatok_capabilities_get` and read `processing_limits.tiktok_analysis`. Accept only TikTok URLs supported by the tool schema. Ask for a missing URL; do not substitute local media or another platform.

TikTok analysis persists a session and consumes plan allowance. Call `creatok_tiktok_video_analyze` with one stable `idempotency_key`; after timeout or `RESULT_UNKNOWN`, reuse that key. It is synchronous: read the returned transcript, vision, metadata and analysis without task polling or a paid-generation estimate. Do not invent absent transcript or metrics.

## Draft and hand off

- Ask only for blocking context, one short question at a time. For a selling video, establish the user's product and core selling points before drafting a product-fit version; ask about price/offer only when material. Reuse known details.
- Decide what to retain at idea level and what to change in structure, expression and visual style; avoid 1:1 copying. Follow the user's preferred closeness to the reference while keeping the execution differentiated. Explain material similarity risks only when relevant.
- Produce a useful first draft quickly: default to an AI-generation-ready script, storyboard and visual direction unless the user requests a live-action shoot plan. Do not force a long brief or fixed creative template.
- Before generation handoff, check the selected model's current duration and reference constraints from capabilities. Explain a segmented plan if needed; never promise automatic stitching. Identify whether recurring people require an identity-preserving Avatar/reference path.
- Route an explicit request to generate the drafted version to installed `creatok-video-generation`. Carry forward the chosen script, product context, references and segment plan. That Skill owns media resolution, preparation, estimate, confirmation, submission and delivery. Do not ask the user to restate the brief. If the Skill is unavailable, present the draft and explain what is needed to continue; do not submit generation from this Skill.

All IDs and URLs must come from MCP results. Use the serialized JSON in the first text result when structured output is unavailable. Keep diagnostic `request_id` values for recovery; never invent source data or handles.
