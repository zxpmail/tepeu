/**
 * API client — fetch wrapper with unified error handling.
 * Returns the unwrapped `data` payload for all endpoints; throws ApiError on non-2xx / non-OK.
 */
import type { Workspace, Memory, FileItem, FileVersion, LlmProvider, ChatSession, ChatMessageBE, ProviderMetadata, SessionStats, Skill } from '../types'

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
  const url = `${BASE_URL}${path}`;
  const res = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers as Record<string, string> },
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

  /** Memory */
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
  writeFile: (path: string, content: string) =>
    request<{ path: string }>('/files/write', {
      method: 'POST',
      body: JSON.stringify({ path, content }),
    }),
  uploadFile: async (file: File, path: string = '/'): Promise<{ path: string; size: number }> => {
    // FormData must NOT set Content-Type (browser sets the multipart boundary). Bypass request().
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', path);
    const res = await fetch(`${BASE_URL}/files/upload`, { method: 'POST', body: formData });
    const body: ApiResponse<{ path: string; size: number }> = await res.json();
    if (!res.ok || body.code !== 'OK') {
      throw new ApiError(body.code, body.message, body.details);
    }
    return body.data;
  },
  deleteFile: (path: string) =>
    request<void>('/files/delete', {
      method: 'POST',
      body: JSON.stringify({ path }),
    }),

  /** File versions */
  getFileHistory: (path: string, workspaceId?: string) =>
    request<{ path: string; versions: FileVersion[] }>(`/files/history?path=${encodeURIComponent(path)}${workspaceId ? `&workspaceId=${workspaceId}` : ''}`),
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
  testProviderConnection: (providerId: string) =>
    request<void>(`/provider/test/${providerId}`, { method: 'POST' }),

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
};
