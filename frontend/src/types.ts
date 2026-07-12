export interface Workspace {
  id: string
  name: string
  description: string | null
  type?: string
  createdAt?: string
}

export interface Memory {
  id: string
  workspaceId: string
  source: string
  content: string
  tags: string[]
  createdAt: string
}

export interface FileItem {
  name: string
  isDirectory: boolean
  size: number
  lastModified?: number
}

export type Theme = 'light' | 'dark' | 'system'

export interface LlmProvider {
  id?: string
  providerId: string
  apiKey: string | null
  baseUrl?: string
  defaultModel?: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ChatSession {
  id: string
  workspaceId: string
  title: string | null
  createdAt?: string
  parentSessionId?: string | null
  forkFromMessageId?: string | null
}

/** 会话级 token/费用/消息统计 */
export interface SessionStats {
  totalTokens: number
  totalCostUsd: number
  turnCount: number
  messageCount: number
  maxHistoryMessages: number
}

export interface ChatMessageBE {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt?: string
}

export interface ProviderModel {
  id: string
  name: string
}

export interface ProviderMetadata {
  id: string
  name: string
  models: ProviderModel[]
  enabled: boolean
}

export interface FileVersion {
  id: string
  workspaceId: string
  filePath: string
  versionNo: number
  contentRef?: string
  createdBySession?: string
  createdAt: string
}

export type Panel = 'workspace' | 'files' | 'chat' | 'memory' | 'terminal' | 'provider' | 'skills'

/** 工作区技能 */
export interface Skill {
  id: string
  workspaceId: string
  slug: string
  name: string
  description: string | null
  content: string
  enabled: boolean
  builtin: boolean
  createdAt?: string
  updatedAt?: string
}
