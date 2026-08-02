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

/** 工作区累计 token/费用（Spec §3.5） */
export interface WorkspaceStats {
  totalTokens: number
  totalCostUsd: number
  turnCount: number
}

/** MCP 状态（Spec M2.2） */
export interface McpStatus {
  enabled: boolean
  clientCount: number
  toolCount: number
  tools: string[]
  resourceCount: number
  resources: Array<{
    server: string
    uri: string
    name: string
    description?: string
    mimeType?: string
  }>
  warning?: string | null
  note: string
}

/** 成本仪表盘 / 预算状态（Spec M2.4） */
export interface BudgetStatus {
  workspaceId: string
  totalTokens: number
  totalCostUsd: number
  turnCount: number
  budgetUsd: number | null
  hardLimit: boolean
  alertThreshold: number
  usageRatio: number
  alert: boolean
  blocked: boolean
}

export interface ChatMessageBE {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'system' | 'tool'
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

export type Panel = 'workspace' | 'files' | 'chat' | 'memory' | 'terminal' | 'provider' | 'skills' | 'multi' | 'cost' | 'schedule'

/** 自主 Agent 定时任务 */
export interface AgentSchedule {
  id: string
  workspaceId: string
  name: string
  prompt: string
  providerId: string
  enabled: boolean
  intervalMinutes: number
  nextRunAt?: string | null
  lastRunAt?: string | null
  lastStatus?: string | null
  lastError?: string | null
  lastSessionId?: string | null
  createdAt?: string
  updatedAt?: string
}

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
