/**
 * API client — fetch wrapper with unified error handling.
 * Returns the unwrapped `data` payload for all endpoints; throws ApiError on non-2xx / non-OK.
 */
import type { Workspace, Memory, FileItem, FileVersion, LlmProvider, ChatSession, ChatMessageBE, ProviderMetadata, SessionStats, WorkspaceStats, BudgetStatus, McpStatus, Skill, AgentSchedule, MarketplaceCatalog } from '../types'
import { ensureInstanceToken, getInstanceTokenSync } from '../security/instanceToken'

const BASE_URL = '/api'

interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  code: string;
  details?: Record<string, unknown>;

  constructor(code: string, message: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.details = details;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  await ensureInstanceToken()
  const url = `${BASE_URL}${path}`;
  const token = getInstanceTokenSync()
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'X-Tepeu-Token': token } : {}),
      ...options.headers as Record<string, string>,
    },
  });

  const body: ApiResponse<T> = await res.json();

  if (!res.ok || body.code !== 'OK') {
    throw new ApiError(body.code, body.message, body.details);
  }

  return body.data;
}

export const api = {
  /** Workspace */
  listWorkspaces: () => request<Workspace[]>('/workspace'),
  getWorkspace: (id: string) => request<Workspace>(`/workspace/${id}`),
  createWorkspace: (name: string, description?: string) =>
    request<Workspace>('/workspace', {
      method: 'POST',
      body: JSON.stringify({ name, description, type: 'personal' }),
    }),
  updateWorkspace: (id: string, data: { name?: string; description?: string }) =>
    request<Workspace>(`/workspace/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deleteWorkspace: (id: string) =>
    request<void>(`/workspace/${id}`, { method: 'DELETE' }),
  switchWorkspace: (id: string) =>
    request<Workspace>(`/workspace/${id}/switch`, { method: 'POST' }),
  /** 工作区累计 token/费用（Spec §3.5） */
  getWorkspaceStats: (id: string) =>
    request<WorkspaceStats>(`/workspace/${id}/stats`),
  /** 成本仪表盘（用量 + 预算状态，Spec M2.4） */
  getWorkspaceCost: (id: string) =>
    request<BudgetStatus>(`/workspace/${id}/cost`),
  updateWorkspaceBudget: (
    id: string,
    body: { budgetUsd?: number | null; hardLimit?: boolean; alertThreshold?: number },
  ) =>
    request<BudgetStatus>(`/workspace/${id}/budget`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  /** Memory */
  listMemories: (params: {
    workspaceId: string
    query?: string
    tags?: string[]
    limit?: number
    cursor?: string
  }) => {
    const q = new URLSearchParams({ workspaceId: params.workspaceId })
    if (params.query) q.set('query', params.query)
    if (params.limit != null) q.set('limit', String(params.limit))
    if (params.cursor) q.set('cursor', params.cursor)
    if (params.tags) {
      for (const t of params.tags) q.append('tags', t)
    }
    return request<{ items: Memory[]; hasMore: boolean; nextCursor?: string }>(
      `/memory?${q.toString()}`,
    )
  },
  searchMemories: (params: { workspaceId: string; query?: string; tags?: string[]; limit?: number; cursor?: string }) =>
    request<{ items: Memory[]; hasMore: boolean; nextCursor?: string }>('/memory/search', {
      method: 'POST',
      body: JSON.stringify(params),
    }),
  createMemory: (workspaceId: string, content: string, source?: string, tags?: string[]) =>
    request<Memory>('/memory', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, content, source, tags }),
    }),
  updateMemory: (id: string, content: string, tags?: string[]) =>
    request<Memory>(`/memory/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ content, tags }),
    }),
  getMemory: (id: string) => request<Memory>(`/memory/${id}`),
  deleteMemory: (id: string) => request<void>(`/memory/${id}`, { method: 'DELETE' }),

  /** Session (chat history) */
  createSession: (workspaceId: string, title?: string) =>
    request<ChatSession>('/session', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, title }),
    }),
  listSessions: (workspaceId: string) =>
    request<ChatSession[]>(`/session?workspaceId=${encodeURIComponent(workspaceId)}`),
  getSession: (id: string) =>
    request<{ session: ChatSession; messages: ChatMessageBE[] }>(`/session/${id}`),
  deleteSession: (id: string) =>
    request<void>(`/session/${id}`, { method: 'DELETE' }),
  /** 重命名会话 */
  renameSession: (id: string, title: string) =>
    request<ChatSession>(`/session/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    }),
  /** 从指定消息处分叉会话 */
  forkSession: (id: string, messageId: string) =>
    request<{ session: ChatSession; messages: ChatMessageBE[] }>(`/session/${id}/fork`, {
      method: 'POST',
      body: JSON.stringify({ messageId }),
    }),
  /** 获取会话 token/费用/消息统计 */
  getSessionStats: (id: string) =>
    request<SessionStats>(`/session/${id}/stats`),

  /** 高危工具审批裁决（Spec M2.3） */
  decideApproval: (id: string, decision: 'approve' | 'deny') =>
    request<{ id: string; sessionId: string; tool: string; status: string }>(
      `/chat/approvals/${id}/decide`,
      { method: 'POST', body: JSON.stringify({ decision }) },
    ),

  /** Files */
  listFiles: (path: string = '/', workspaceId?: string) => {
    const qs = new URLSearchParams({ path })
    if (workspaceId) qs.set('workspaceId', workspaceId)
    return request<{ path: string; items: FileItem[] }>(`/files/list?${qs.toString()}`)
  },
  /** 浏览器可直接打开的原始文件 URL（HTML/PDF/图片预览用） */
  rawFileUrl: (path: string, workspaceId?: string) => {
    const qs = new URLSearchParams({ path })
    if (workspaceId) qs.set('workspaceId', workspaceId)
    return `${BASE_URL}/files/raw?${qs.toString()}`
  },
  readFile: (path: string, workspaceId?: string) =>
    request<{ path: string; content: string; mimeType: string }>('/files/read', {
      method: 'POST',
      body: JSON.stringify(workspaceId ? { path, workspaceId } : { path }),
    }),
  writeFile: (path: string, content: string, workspaceId?: string) =>
    request<{ path: string }>('/files/write', {
      method: 'POST',
      body: JSON.stringify(workspaceId ? { path, content, workspaceId } : { path, content }),
    }),
  uploadFile: async (file: File, path: string = '/', workspaceId?: string): Promise<{ path: string; size: number }> => {
    // FormData must NOT set Content-Type (browser sets the multipart boundary). Bypass request().
    await ensureInstanceToken()
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', path);
    if (workspaceId) formData.append('workspaceId', workspaceId);
    const token = getInstanceTokenSync()
    const res = await fetch(`${BASE_URL}/files/upload`, {
      method: 'POST',
      body: formData,
      headers: token ? { 'X-Tepeu-Token': token } : {},
    });
    const body: ApiResponse<{ path: string; size: number }> = await res.json();
    if (!res.ok || body.code !== 'OK') {
      throw new ApiError(body.code, body.message, body.details);
    }
    return body.data;
  },
  /**
   * 删除工作区内文件（REST 沙箱免批；需实例令牌）。Agent delete_file 仍走审批。
   */
  deleteFile: (path: string, workspaceId?: string) =>
    request<void>('/files/delete', {
      method: 'POST',
      body: JSON.stringify(workspaceId ? { path, workspaceId } : { path }),
    }),

  /** File versions */
  getFileHistory: (path: string, workspaceId?: string) =>
    request<{ path: string; versions: FileVersion[] }>(`/files/history?path=${encodeURIComponent(path)}${workspaceId ? `&workspaceId=${workspaceId}` : ''}`),
  /** 读取某版本快照原文（GET /files/preview/{id}） */
  getVersionContent: async (versionId: string): Promise<string> => {
    const res = await fetch(`${BASE_URL}/files/preview/${encodeURIComponent(versionId)}`)
    if (!res.ok) {
      throw new ApiError('IO_ERROR', `读取版本失败 HTTP ${res.status}`)
    }
    return res.text()
  },
  restoreFileVersion: (versionId: string) =>
    request<{ id: string; workspaceId: string; filePath: string; versionNo: number; createdAt: string }>(`/files/restore/${versionId}`, { method: 'POST' }),
  createFileVersion: (workspaceId: string, path: string, content: string, sessionId?: string) =>
    request<{ id: string; workspaceId: string; filePath: string; versionNo: number; createdAt: string }>('/files/version', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, path, content, sessionId }),
    }),

  /** Provider config */
  getAvailableProviders: () => request<ProviderMetadata[]>('/provider/available'),
  getProviderConfig: (providerId: string) =>
    request<LlmProvider>(`/provider/config/${providerId}`),
  saveProviderConfig: (providerId: string, config: {
    apiKey?: string; baseUrl?: string; defaultModel?: string; enabled?: boolean
  }) =>
    request<LlmProvider>(`/provider/config/${providerId}`, {
      method: 'PUT',
      body: JSON.stringify({ providerId, ...config }),
    }),
  /** 连接测试；可传草稿凭证（未保存也可测） */
  testProviderConnection: (providerId: string, draft?: {
    apiKey?: string; baseUrl?: string; defaultModel?: string
  }) =>
    request<void>(`/provider/test/${providerId}`, {
      method: 'POST',
      body: JSON.stringify(draft ?? {}),
    }),

  /** Skills */
  listSkills: (workspaceId: string) =>
    request<Skill[]>(`/skills?workspaceId=${encodeURIComponent(workspaceId)}`),
  installSkill: (workspaceId: string, content: string, name?: string) =>
    request<Skill>('/skills', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, content, name }),
    }),
  /** 从 URL 安装（.md 或 .zip） */
  installSkillFromUrl: (workspaceId: string, url: string, name?: string) =>
    request<Skill>('/skills', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, url, name }),
    }),
  /** 上传 ZIP 技能包 */
  installSkillFromZip: async (workspaceId: string, file: File, name?: string): Promise<Skill> => {
    const form = new FormData()
    form.append('workspaceId', workspaceId)
    form.append('file', file)
    if (name) form.append('name', name)
    const res = await fetch(`${BASE_URL}/skills/upload`, { method: 'POST', body: form })
    const body: ApiResponse<Skill> = await res.json()
    if (!res.ok || body.code !== 'OK') {
      throw new ApiError(body.code, body.message, body.details)
    }
    return body.data
  },
  /** 一键安装 ReqForge 编程套件（skill + agent） */
  installReqForgeCodingPack: (workspaceId: string) =>
    request<{ installed: number; failed: number; errors: string[]; skills: Skill[] }>(
      `/skills/packs/reqforge-coding?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: 'POST' },
    ),
  setSkillEnabled: (id: string, enabled: boolean) =>
    request<Skill>(`/skills/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    }),
  deleteSkill: (id: string) =>
    request<void>(`/skills/${id}`, { method: 'DELETE' }),

  /** 应用市场（Spec M3.3） */
  getMarketplaceCatalog: (workspaceId?: string, q?: string) => {
    const params = new URLSearchParams()
    if (workspaceId) params.set('workspaceId', workspaceId)
    if (q) params.set('q', q)
    const qs = params.toString()
    return request<MarketplaceCatalog>(
      `/marketplace/catalog${qs ? `?${qs}` : ''}`,
    )
  },
  installMarketplaceSkill: (workspaceId: string, entryId: string) =>
    request<Skill>('/marketplace/install', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, entryId }),
    }),

  /** 自主 Agent 定时任务（Spec M3.1） */
  listSchedules: (workspaceId: string) =>
    request<AgentSchedule[]>(`/schedules?workspaceId=${encodeURIComponent(workspaceId)}`),
  createSchedule: (body: {
    workspaceId: string
    name: string
    prompt: string
    providerId: string
    intervalMinutes: number
    enabled?: boolean
  }) =>
    request<AgentSchedule>('/schedules', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateSchedule: (
    id: string,
    body: {
      name?: string
      prompt?: string
      providerId?: string
      intervalMinutes?: number
      enabled?: boolean
    },
  ) =>
    request<AgentSchedule>(`/schedules/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  deleteSchedule: (id: string) =>
    request<void>(`/schedules/${id}`, { method: 'DELETE' }),
  runSchedule: (id: string) =>
    request<AgentSchedule>(`/schedules/${id}/run`, { method: 'POST' }),

  /** MCP 状态（Spec M2.2） */
  getMcpStatus: () => request<McpStatus>('/mcp/status'),
  refreshMcpStatus: () =>
    request<McpStatus>('/mcp/refresh', { method: 'POST' }),
  /** 读取 MCP 资源正文 */
  readMcpResource: (uri: string) =>
    request<{ uri: string; content: string }>('/mcp/resources/read', {
      method: 'POST',
      body: JSON.stringify({ uri }),
    }),

  /** Slash 命令（Phase 14，不经 LLM） */
  listSlashCommands: () =>
    request<Array<{
      name: string
      description: string
      usage: string
      requiresWorkspace: boolean
    }>>('/slash/commands'),
  executeSlash: (command: string, workspaceId?: string, sessionId?: string) =>
    request<{ command: string; text: string; action?: string }>('/slash', {
      method: 'POST',
      body: JSON.stringify({ command, workspaceId, sessionId }),
    }),
};
