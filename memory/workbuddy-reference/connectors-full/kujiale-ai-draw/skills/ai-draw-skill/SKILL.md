name: ai-draw-skill
description: 上传图片并通过酷家乐 AI 绘图生成效果图。用户要求上传图片、生成效果图、选择设计场景或 AI 绘图时使用。
---

# AI 绘图

使用 `kujiale-ai-draw` MCP完成图片上传后的选场景、计价、余额检查、任务创建和结果展示。

## 图片上传（OUS）

固定使用业务 `guid=site-aigc`。确认本地文件存在、可读且 MIME 类型为 `image/*`；用户提供 URL 时直接使用，不重复上传。

1. **获取 Token**
   - 调用 `kujiale-ai-draw_get_generate_upload_token` MCP 工具（无参数）。
   - 读取返回的 `d.ousToken` 和 `d.globalDomain`；后续请求统一携带 Header `ous-token: <ousToken>`。
2. **获取分片规则**
   - `GET {globalDomain}/ous/api/block/upload/spilt/rule?guid=site-aigc`（路径确为 `spilt`）。
   - 读取 `d.blockSize` 和 `d.domain`，后续上传使用 `d.domain`；`blockSize` 缺失时按 `1 MiB` 处理。
3. 计算完整图片的字节数、MD5 和原始文件名，根据 `file.size > blockSize` 选择上传方式。
4. **小文件直传**（`file.size <= blockSize`）
   - `POST {domain}/ous/api/single/upload`
   - `multipart/form-data`：`guid=site-aigc`、`md5`、`file`。
   - 保存响应 `d.obsTaskId`。
5. **大文件分片上传**（`file.size > blockSize`）
   - 分片数为 `ceil(file.size/blockSize)`，按 `blockSize` 切片，编号从 `1` 开始。
   - 初始化：`POST {domain}/ous/api/block/upload/init`，表单传 `guid=site-aigc`、`md5`、`blocks`、`size`、`name`，保存响应 `d.obsTaskId`。
   - 上传每片：`POST {domain}/ous/api/block/upload/part`，表单传 `guid=site-aigc`、`obstaskid`、`block`、`file`。最多并发 `3` 片，每片最多尝试 `3` 次。
   - 用户取消且已取得 `obsTaskId` 时：中止进行中的请求，并调用 `POST {domain}/ous/api/block/upload/abort?obstaskid={obsTaskId}`。
6. **查询结果**
   - 直传完成或全部分片完成后，轮询 `GET {domain}/ous/api/upload/status?obstaskid={obsTaskId}`，间隔 `2` 秒，最多等待 `2` 分钟。
   - `status=5`：成功，保存 `d.url` 和 `d.uploadKey`。
   - `status=6`：失败，返回 `d.errorCode`、`d.errorMsg` 并停止。
   - `status=0..4`：继续轮询。

任一必要接口 HTTP 失败、业务字段 `c` 非 `0` 或缺少必要字段时立即停止并报告原始错误；不要伪造 URL，也不要在失败响应中返回 URL。

## 生成流程

MCP 调用必须使用服务返回的字段名和值原样向后传递，不猜测枚举，不自行拼装场景或价格。

1. **选择场景**
   - 图片上传成功后，调用 `get_feature_options`，按工具定义传入图片 URL（以及工具要求的其他字段）。
   - 将返回的全部可选场景展示给用户，显示名称和必要说明，但提交时使用选项的原始值。
   - 等待用户明确选择；只有一个选项也要确认，不代替用户决定。

2. **确认价格**
   - 使用图片信息和用户所选场景调用 `get_price`。
   - 向用户展示返回的价格、数量和单位，并询问是否确认支付。
   - 只有用户明确确认后才能继续；拒绝或取消时停止，不查询余额、不创建任务。

3. **检查余额**
   - 调用 `get_balance`。
   - 按 MCP 返回的同一计价单位比较可用余额与已确认价格。
   - 余额不足时，不调用 `create_task`；向用户提供充值链接：
     https://www.kujiale.com/pcenter/vip?kioid=site-nav-user-vip-link&mode=purchase
   - 等待用户完成充值后再次调用 `get_balance`。余额仍不足则继续提示，不创建任务。

4. **创建任务**
   - 余额充足后调用一次 `create_task`，传入图片 URL、已选场景以及前序 MCP 返回并要求透传的参数。
   - 保存返回的任务 ID。调用结果不确定时先查询任务状态，不要直接重试创建，避免重复扣费。

5. **等待结果**
   - 使用任务 ID 调用 `get_task_detail`；未完成时每隔 5 秒轮询一次，最多等待 10 分钟。
   - 进行中：继续轮询，可简短告知用户仍在生成。
   - 成功：提取返回的全部结果图片 URL，并用 Markdown 图片语法逐张展示：`![生成结果](图片URL)`；同时保留可点击原始链接。
   - 失败或取消：立即停止，展示 MCP 返回的真实状态和错误，不伪造结果、不再次创建任务。
   - 超时：告知用户任务仍在处理并返回任务 ID，用户要求后可继续查询同一任务。

## 交互约束

- 必须保留两个用户确认点：场景选择、价格确认。
- 价格确认后若参数发生变化，必须重新调用 `get_price` 并再次确认。
- 在余额充足且用户确认价格前，绝不调用 `create_task`。
- 同一轮生成只创建一个任务；轮询始终使用该任务 ID。
