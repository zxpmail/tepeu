---
name: luoge-uc
description: 罗格用户中心账号技能 - 发送短信验证码、手机号验证码登录、查询当前登录用户信息
version: "1.0.0"
author: "Luoge"
---

# 罗格用户中心 Skill

本 Skill 提供罗格用户中心（ns-uc）的账号能力：发送短信验证码、手机号验证码登录、查询当前登录用户，以及 OAuth 2.1 授权码换取令牌。

## 认证与授权

- 本 Connector 使用 **MCP OAuth 2.1**，WorkBuddy 在首次连接时会自动打开浏览器完成授权，无需手工填写任何端点或密钥。
- 认证失败（返回 401）时，请用户在 WorkBuddy 中重新连接「罗格用户中心」Connector，然后仅重试一次被中断的操作。
- 不要向用户索取、保存、显示或转发任何 Token；也不要手工添加 `Authorization` 请求头，除非该请求的工具参数明确要求。

## 可用工具

### sendSmsCode - 发送手机验证码

向指定手机号发送短信验证码，用于登录。同一手机号一分钟内不可重复发送。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| mobile | string | ✅ | 11 位手机号 |
| type | integer | - | 验证码用途：1-登录，2-找回密码，3-注册。默认 1 |
| check | integer | - | 是否校验手机号已注册：2-校验（手机号必须已是系统用户），其他值不校验 |

**使用示例**：登录前先调用 `sendSmsCode`，`type` 传 1、`mobile` 传用户手机号。

### signInByMobile - 手机号验证码登录

使用手机号与短信验证码登录，成功返回访问令牌 accessToken（JWT），后续调用其它接口需在 Authorization 头中携带。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| type | integer | ✅ | 登录方式，固定传 2 表示手机号加验证码 |
| mobile | string | ✅ | 11 位手机号 |
| verifyCode | string | ✅ | 短信验证码 |
| regType | integer | - | 注册来源：3-web 端；10-实施项目 |

**使用示例**：先 `sendSmsCode` 发送验证码，用户提供验证码后调用 `signInByMobile`，`type` 固定传 2。

### getMyProfile - 查询当前登录用户

用登录或授权得到的 accessToken 查询当前用户的账号信息。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| Authorization | string | ✅ | Bearer 加访问令牌，例如 `Bearer eyJ0eXAiOiJKV1Q...` |

返回字段：`userId`（用户ID）、`orgId`（机构ID）、`name`（姓名）、`username`（登录名）、`mobile`（手机号）、`email`（邮箱）、`avatar`（头像地址）。

### exchangeOAuthToken - 授权码换取令牌

OAuth 2.1 授权码模式，用回调拿到的 code 加 PKCE code_verifier 换取访问令牌。**该流程通常由 WorkBuddy 自动完成**，一般无需手动调用。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| grant_type | string | ✅ | 固定传 `authorization_code` |
| code | string | ✅ | 授权回调返回的授权码 |
| client_id | string | ✅ | 第三方应用标识 |
| code_verifier | string | ✅ | PKCE 校验串，其 SHA256 结果需等于授权时的 code_challenge |
| redirect_uri | string | - | 必须与发起授权时一致的回调地址 |

## 注意事项

- 所有接口返回统一结构 `{ code, message, data }`，`code` 为 200 表示成功，其它为业务错误码。
- 短信验证码同一手机号一分钟内不可重复发送；发送失败时提示用户稍后重试。
- Token 过期时，提示用户前往 WorkBuddy 设置页重新授权「罗格用户中心」。
- 不要猜测或编造用户信息；`getMyProfile` 返回什么就如实转述什么。
