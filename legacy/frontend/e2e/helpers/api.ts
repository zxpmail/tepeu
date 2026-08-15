/**
 * E2E 直连后端 API（不经前端 mock）— 预置服务商/工作区/记忆/文件。
 * 关联：playwright.config webServer、各 *.spec.ts、ccSwitch DeepSeek
 */
import { expect, type APIRequestContext } from '@playwright/test'
import { loadDeepSeekFromCcSwitch } from './ccSwitch'

const API = process.env.API_URL || 'http://localhost:30141'

interface Envelope<T> {
  code: string
  message: string
  data: T
}

let cachedInstanceToken: string | null | undefined

/** 拉取本机实例令牌（保护写文件/审批等） */
export async function getInstanceToken(request: APIRequestContext): Promise<string | null> {
  if (cachedInstanceToken !== undefined) return cachedInstanceToken
  try {
    const data = await unwrap<{ enabled: boolean; token: string | null }>(
      await request.get(`${API}/api/security/instance-token`),
    )
    cachedInstanceToken = data.enabled ? (data.token ?? null) : null
  } catch {
    cachedInstanceToken = null
  }
  return cachedInstanceToken
}

async function authHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const token = await getInstanceToken(request)
  return token ? { 'X-Tepeu-Token': token } : {}
}

async function unwrap<T>(res: Awaited<ReturnType<APIRequestContext['fetch']>>): Promise<T> {
  expect(res.ok(), `HTTP ${res.status()} ${res.url()}`).toBeTruthy()
  const body = (await res.json()) as Envelope<T>
  expect(body.code, body.message).toBe('OK')
  return body.data
}

/**
 * 确保有可用服务商（跳过 SetupWizard）。
 * 优先从 CC Switch 导入真实 DeepSeek；否则写入假 openai 密钥。
 * @returns 实际启用的 providerId
 */
export async function ensureProvider(request: APIRequestContext): Promise<string> {
  const ds = loadDeepSeekFromCcSwitch()
  if (ds) {
    // 关掉其它已启用服务商，避免对话页默认选到假 openai
    for (const id of ['openai', 'anthropic', 'ollama']) {
      await request.put(`${API}/api/provider/config/${id}`, {
        data: { providerId: id, enabled: false },
      }).catch(() => {})
    }
    await unwrap(
      await request.put(`${API}/api/provider/config/deepseek`, {
        data: {
          providerId: 'deepseek',
          apiKey: ds.apiKey,
          baseUrl: ds.baseUrl,
          defaultModel: ds.defaultModel || 'deepseek-v4-flash',
          enabled: true,
        },
      }),
    )
    return 'deepseek'
  }

  await unwrap(
    await request.put(`${API}/api/provider/config/openai`, {
      data: {
        providerId: 'openai',
        apiKey: 'sk-e2e-test-key-not-real',
        defaultModel: 'gpt-4o',
        enabled: true,
      },
    }),
  )
  return 'openai'
}

/** 创建并返回工作区 */
export async function createWorkspace(
  request: APIRequestContext,
  name: string,
  description = 'playwright e2e',
): Promise<{ id: string; name: string }> {
  return unwrap(
    await request.post(`${API}/api/workspace`, {
      data: { name, description, type: 'personal' },
    }),
  )
}

/** 列出工作区 */
export async function listWorkspaces(
  request: APIRequestContext,
): Promise<Array<{ id: string; name: string }>> {
  return unwrap(await request.get(`${API}/api/workspace`))
}

/**
 * 确保有工作区。优先返回列表第一项（与前端 useWorkspace 默认 current 一致），
 * 否则新建。
 */
export async function ensureWorkspace(
  request: APIRequestContext,
  name = `e2e-ws-${Date.now()}`,
): Promise<string> {
  await ensureProvider(request)
  const existing = await listWorkspaces(request)
  if (existing.length > 0) return existing[0]!.id
  const ws = await createWorkspace(request, name)
  return ws.id
}

/** 写文件（真实后端） */
export async function writeFile(
  request: APIRequestContext,
  path: string,
  content: string,
  workspaceId?: string,
): Promise<void> {
  const headers = await authHeaders(request)
  await unwrap(
    await request.post(`${API}/api/files/write`, {
      headers,
      data: workspaceId ? { path, content, workspaceId } : { path, content },
    }),
  )
}

/** 创建记忆 */
export async function createMemory(
  request: APIRequestContext,
  workspaceId: string,
  content: string,
  tags: string[] = ['e2e'],
): Promise<{ id: string; content: string }> {
  return unwrap(
    await request.post(`${API}/api/memory`, {
      data: { workspaceId, content, source: 'e2e', tags },
    }),
  )
}

/** 更新预算（可选硬门禁） */
export async function updateBudget(
  request: APIRequestContext,
  workspaceId: string,
  budgetUsd: number | null,
  opts: { hardLimit?: boolean; alertThreshold?: number } = {},
): Promise<void> {
  await unwrap(
    await request.put(`${API}/api/workspace/${workspaceId}/budget`, {
      data: {
        budgetUsd,
        hardLimit: opts.hardLimit ?? false,
        alertThreshold: opts.alertThreshold ?? 0.8,
      },
    }),
  )
}

/** 健康检查 */
export async function waitApiHealthy(request: APIRequestContext): Promise<void> {
  const res = await request.get(`${API}/actuator/health`)
  expect(res.ok()).toBeTruthy()
}
