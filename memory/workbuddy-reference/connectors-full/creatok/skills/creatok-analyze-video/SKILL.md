---
name: creatok-analyze-video
display_name: CreatOK 视频分析
display_name_en: CreatOK Video Analysis
description: Analyze a TikTok video’s script, scenes, hook and creative structure. Not Shop Analytics performance reporting.
description_zh: 分析 TikTok 视频的脚本、分镜、钩子和创意结构；不查询店铺经营数据。
description_en: Analyze a TikTok video’s script, scenes, hook and creative structure. Not Shop Analytics performance reporting.
category: media
version: 0.1.0
author: CreatOK
allowed-tools: creatok_capabilities_get, creatok_tiktok_video_analyze
---

# CreatOK Video Analysis

Read and apply [`./references/user-experience.md`](./references/user-experience.md) before choices, confirmations, presentation, or handoffs.

## Non-negotiable credential boundary

Use only the connected CreatOK MCP tools above for CreatOK operations. Never use curl, Shell, PowerShell, browser requests, generic HTTP tools, direct endpoint calls, or a local CLI as fallback. Local execution is not needed for this workflow. Never request or read API keys, tokens, `Authorization` headers, connector configuration, credential files, or environment variables including `CREATOK_API_KEY`; never display or transmit them. If secret-like content appears unexpectedly, suppress it, report only the stable error code and `request_id`, and tell the user to rotate the key. For `WORKSPACE_REQUIRED`, ask the user to reconnect the connector and pick an organization workspace during authorization; never ask for a credential in chat.

## CreatOK-only execution boundary

When the user explicitly asks for CreatOK or selects this connector, every CreatOK business operation must use a connected CreatOK MCP tool in `allowed-tools`. Never call WorkBuddy built-in ImageGen or video generation, another connector, another provider, or any host-provided generation or processing capability as a fallback. If a required CreatOK tool is missing from the current tool context or a CreatOK call fails, stop the requested operation and report the missing tool or stable MCP `code` and `request_id`. Only follow an explicit CreatOK recovery path such as same-key idempotency replay or status lookup; never continue through a non-CreatOK fallback. Do not use local execution for this workflow.

Model `id` and `default_model` values are internal CreatOK tool arguments. In every user-visible model list, choice, summary, estimate, confirmation, or result, show only the model `label`; never show model identifiers.

## Analyze the reference

Call `creatok_capabilities_get` and read `processing_limits.tiktok_analysis`. Accept only TikTok URLs supported by the tool schema. Ask for a missing URL; do not substitute local media or another platform.

TikTok analysis persists a session and consumes plan allowance. Call `creatok_tiktok_video_analyze` with one stable `idempotency_key`; after timeout or `RESULT_UNKNOWN`, reuse that key. It is synchronous: read the returned transcript, vision, metadata and analysis without task polling or a paid-generation estimate. Do not invent absent transcript or metrics.

## Present the analysis

Infer the content type to choose useful analysis: selling videos need proof, trust and conversion logic; demos need demonstration order; before/after needs contrast and payoff; reviews need credibility; entertainment needs pacing and emotional pull. Do not force a selling interpretation on non-selling content.

Show a timestamped original script and a storyboard table with time range, scene summary, visual action, and speech/on-screen text. Summarize available duration and public engagement metrics without invented values. Explain hook, pacing, proof and CTA where relevant; distinguish source observations from your interpretation.

Offer up to three short numbered next steps appropriate to the content. Route adaptation to `creatok-recreate-video`, carrying forward the full analysis and any known product context so the user does not have to repeat it. Do not automatically generate or optimize a prompt.

All IDs and URLs must come from MCP results. Use the serialized JSON in the first text result when structured output is unavailable. Keep diagnostic `request_id` values for recovery; never invent source data or handles.
