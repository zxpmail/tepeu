---
name: atlas-cloud
display_name: Atlas Cloud 使用指南
display_name_en: Atlas Cloud Guide
description: >-
  How to use the Atlas Cloud connector tools (atlas_*) correctly: find a model in
  the live catalog, read its schema and price, submit image / video / audio /
  transcription jobs, handle the server's quote-and-confirm step, poll results,
  upload local files, and deliver outputs. Use whenever an atlas_* tool is about
  to be called or the user asks to generate images, video, voice or music with
  Atlas Cloud.
description_zh: 指导 AI 正确使用 Atlas Cloud 连接器：查模型、看价格、提交生成、走确认流程、轮询结果、上传本地文件、交付产物。
description_en: Guides the AI through Atlas Cloud connector tools — model lookup, pricing, generation, confirmation, polling, uploads and delivery.
version: 1.0.0
author: Atlas Cloud
---

# Atlas Cloud 连接器使用指南

Atlas Cloud 连接器通过一个统一接口提供三百多个生成模型（图像、视频、语音、音乐、转写）。
用户通过账号授权连接，**生成费用记在用户自己的 Atlas 账户上**。

## 工具一览

| 工具 | 用途 | 是否计费 |
| --- | --- | --- |
| `atlas_list_models` | 按类型列出模型 | 否 |
| `atlas_search_docs` | 按关键词搜模型和文档 | 否 |
| `atlas_get_model_info` | 查单个模型的参数 schema、枚举值、默认值、定价 | 否 |
| `atlas_get_model_costs` | 生成前估算这一次的费用 | 否 |
| `atlas_get_upload_url` | 换上传票据，把本地文件变成公网 URL | 否 |
| `atlas_generate_image` / `atlas_generate_video` / `atlas_generate_audio` | 提交生成任务 | **是** |
| `atlas_transcribe_audio` | 音频转文字 | **是** |
| `atlas_quick_generate` | 一步出结果，适合简单单张图 | **是** |
| `atlas_get_prediction` | 查询异步任务状态和结果 | 否 |
| `atlas_list_predictions` | 列最近的任务，找回丢失的任务 ID | 否 |
| `atlas_get_balance` / `atlas_get_model_usage` | 查余额和用量 | 否 |

所有操作都走这些工具。**不要用 curl 直接调 Atlas 的 REST API**：这里是账号授权连接，
环境里没有 API Key，直接调只会 401。唯一的例外是上传（见下文）。

## 1. 模型和参数一律现查

模型目录经常变。每次生成前：

1. 用 `atlas_search_docs` 或 `atlas_list_models` 找候选模型
2. 用 `atlas_get_model_info` 取该模型的参数 schema、枚举值、默认值和定价
3. 严格按 schema 构造参数

不要凭记忆写模型 ID 或参数名。常见错误：模型要 `ratio` 却传了 `aspect_ratio`，
要 `image` 却传了 `image_url`。schema 里没有的参数就是不存在。

## 2. 提交前报价

提交计费任务前，用 `atlas_get_model_costs` 估算费用并告诉用户。
能力相当的模型价差可能很大（视频每秒单价能差三倍以上），先比价再推荐。

## 3. 幂等键与服务端确认

五个计费工具都要求 `idempotency_key`（一个 UUID）：

- **每次新的生成都换一个新的 key**；只有重试完全相同的请求才复用旧 key。

调用后可能拿到的不是任务 ID，而是**一份报价和一个 `confirmation_token`**。
这表示服务端**没有提交、没有扣费**，在等用户确认。这不是失败：

1. 把报价原样告诉用户，停下等回复。
2. 用户明确同意后，用**同一个 `idempotency_key`**、**一字未改的参数**、加上 `confirmation_token` 再调一次。
3. 换了 key 或改了参数，令牌就失效，会再被拦一次。不要反复换 key 重试。

视频任务按秒计费，服务端算不出精确价时一律转确认，金额再小也一样。

## 4. 轮询与结果

提交成功后拿到 prediction ID，用 `atlas_get_prediction` 轮询。

- 返回成功状态之前，**不要说任务完成，也不要给出任何产物链接**。
- `failed` 是终点，再轮询也不会变。把 `error` 原文告诉用户，建议换一个能力相当的模型或原样重提。
  `No available upstream channel` / `Upstream access denied` 是上游路由问题，换模型能绕开。
- 提交时的金额是预估；**失败的任务不计费**。确认成功之前说"预计 $X"，不要说"已扣费"。
- 找不到之前的任务 ID 时，用 `atlas_list_predictions` 找回。

## 5. 上传本地文件

远程服务读不到用户本机的文件，本地路径直接填进生成参数一定失败。

1. 调 `atlas_get_upload_url`，拿到 `upload_url`、`expires_at`、`max_bytes` 和 `curl_example`。
2. 按 `curl_example` 把文件 POST 上去，文件名（带扩展名）放在 `X-Atlas-Filename` 头里：

   ```bash
   curl -sS -X POST "<upload_url>" \
     -H "Content-Type: application/octet-stream" \
     -H "X-Atlas-Filename: product.jpg" \
     --data-binary @"/path/to/product.jpg"
   ```

3. 响应里的 `url` 就是公网地址，填进 `image_url` 或音频参数。

- 票据已带身份，这条 curl **不需要**任何 key。
- 一个 `upload_url` 在有效期内（约 10 分钟）可传多个文件。上传不计费。
- 超过 `max_bytes`（默认 32MB）会被拒，请用户提供公网链接。
- curl 失败或响应里没有 `url`，如实说没传上去，不要写成"已上传"。

## 6. 交付

- 远端 URL **原样复制**，不要编造、拼接或改写。
- 图片用 `![描述](URL)`，视频音频用带说明的链接，让界面有机会渲染预览。
- 同时注明完整模型 ID（如 `bytedance/seedream-v5.0-lite`）和费用。
- 需要把产物存到本地时，下载失败（502、超时、空文件）要如实说，并给出远端链接。
  产出文件存在海外对象存储，国内网络直连有时不通，换网络或代理通常能解决。

## 7. 授权与失败

- 看不到 `atlas_*` 工具，只说明它们还没载入，不说明授权有问题。只有真正调用失败才能判定不可用。
- 调用失败时如实转述错误原文，不要模拟结果，不要换别的通道顶替。
- 不要替用户生成涉及真实人物肖像、他人版权角色或违规内容的素材。
