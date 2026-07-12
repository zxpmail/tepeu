/**
 * 对话面板 — IDE 中栏：消息分组、过程折叠、小地图、卡片输入。
 * 关联：useChat、ChatInput、MessageView、ProcessDetails、ChatMinimap、IdeShell。
 */
import { useState, useEffect, useRef, useMemo } from 'react'
import { api } from '../../api/client'
import { useChat, type LastUsage } from '../../hooks/useChat'
import ChatInput from '../chat/ChatInput'
import MessageView from '../chat/MessageView'
import ProcessDetails from '../chat/ProcessDetails'
import ChatMinimap, { toMinimapSources, useItemRefs } from '../chat/ChatMinimap'
import { groupChatMessages } from '../chat/groupMessages'
import type { ProviderMetadata, Panel, SessionStats } from '../../types'

interface ChatViewProps {
  workspaceId: string | undefined
  onNavigate?: (panel: Panel) => void
  onOpenFile?: (path: string) => void
  /** IDE 壳模式：隐藏自带顶栏会话选择，向上同步状态 */
  ideMode?: boolean
  onSessionChange?: (sessionId: string | undefined) => void
  onStatsChange?: (stats: {
    lastUsage: LastUsage | null
    sessionStats: SessionStats | null
    queueLength: number
  }) => void
  onRegisterActions?: (actions: {
    reset: () => void
    loadSession: (id: string) => Promise<void>
  }) => void
}

export default function ChatView({
  workspaceId,
  onNavigate,
  onOpenFile,
  ideMode = false,
  onSessionChange,
  onStatsChange,
  onRegisterActions,
}: ChatViewProps) {
  const {
    messages, streaming, error, sessionId,
    send, stop, reset, loadSession,
    lastUsage, sessionStats, queueLength,
  } = useChat()
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [provider, setProvider] = useState('')
  const [input, setInput] = useState('')
  const [sessions, setSessions] = useState<{ id: string; title: string | null }[]>([])
  const [forking, setForking] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const displayItems = useMemo(() => groupChatMessages(messages), [messages])
  const minimapSources = useMemo(() => toMinimapSources(displayItems), [displayItems])
  const itemRefs = useItemRefs(minimapSources.length)

  useEffect(() => {
    api.getAvailableProviders()
      .then(list => {
        const enabled = list.filter(p => p.enabled)
        const pick = enabled.length > 0 ? enabled : list
        setProviders(pick.length > 0 ? pick : list)
        if (pick.length > 0) setProvider(pick[0]!.id)
        else if (list.length > 0) setProvider(list[0]!.id)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!workspaceId || ideMode) { setSessions([]); return }
    api.listSessions(workspaceId).then(setSessions).catch(() => {})
  }, [workspaceId, sessionId, messages.length, ideMode])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    onSessionChange?.(sessionId)
  }, [sessionId, onSessionChange])

  useEffect(() => {
    onStatsChange?.({ lastUsage, sessionStats, queueLength })
  }, [lastUsage, sessionStats, queueLength, onStatsChange])

  useEffect(() => {
    onRegisterActions?.({ reset, loadSession })
  }, [reset, loadSession, onRegisterActions])

  const handleSend = () => {
    if (!workspaceId) return
    send(input, workspaceId, provider)
    setInput('')
  }

  const handleSlashCommand = (cmd: string) => {
    if (cmd === 'clear' || cmd === 'new') {
      reset()
    } else if (cmd === 'files') {
      onNavigate?.('files')
    }
  }

  const handleFork = async (messageId: string) => {
    if (!sessionId || forking) return
    setForking(messageId)
    try {
      const result = await api.forkSession(sessionId, messageId)
      const newId = result.session?.id
      if (newId) await loadSession(newId)
    } catch (e) {
      console.error(e)
    } finally {
      setForking(null)
    }
  }

  if (!workspaceId) {
    return (
      <div className="chat-panel h-full flex items-center justify-center p-4" style={{ color: 'var(--color-text-secondary)' }}>
        请先选择或创建工作区（左下角「工作区」）。
      </div>
    )
  }

  const prompt = lastUsage?.promptTokens ?? 0
  const comp = lastUsage?.completionTokens ?? 0
  const cost = sessionStats?.totalCostUsd ?? lastUsage?.costUsd ?? 0
  const msgCount = sessionStats?.messageCount ?? messages.filter(m => m.role !== 'tool').length
  const maxHist = sessionStats?.maxHistoryMessages ?? 50
  const canChat = providers.length > 0

  /** 给小地图节点挂 ref（顺序与 minimapSources 一致） */
  let refIndex = 0
  const bindRef = () => {
    const i = refIndex++
    return (el: HTMLDivElement | null) => {
      if (!itemRefs.current) return
      itemRefs.current[i] = el
    }
  }

  return (
    <div className="chat-panel flex flex-col h-full overflow-hidden">
      {!ideMode && (
        <div
          className="shrink-0 h-10 flex items-center gap-2 px-3 border-b"
          style={{
            borderColor: 'var(--color-border)',
            backgroundColor: 'var(--color-bg-secondary)',
          }}
        >
          <select
            value={provider}
            onChange={e => setProvider(e.target.value)}
            className="chat-select"
          >
            {providers.length === 0 && <option value="">无可用服务商</option>}
            {providers.map(p => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <select
            value={sessionId ?? ''}
            onChange={e => { if (e.target.value === '') reset(); else loadSession(e.target.value) }}
            className="chat-select max-w-[36%]"
          >
            <option value="">+ 新建会话</option>
            {sessions.map(s => (
              <option key={s.id} value={s.id}>{s.title || s.id.slice(0, 8)}</option>
            ))}
          </select>
          <div className="chat-stats ml-auto flex items-center gap-1.5 whitespace-nowrap">
            <span>↑{prompt}</span>
            <span>↓{comp}</span>
            <span>·</span>
            <span>~${cost.toFixed(4)}</span>
            <span>·</span>
            <span>{msgCount}条</span>
            <span>·</span>
            <span>≤{maxHist}</span>
            {queueLength > 0 && (
              <>
                <span>·</span>
                <span>排队{queueLength}</span>
              </>
            )}
          </div>
          <button type="button" onClick={reset} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>
            新建
          </button>
        </div>
      )}

      <div className="flex flex-1 min-h-0 overflow-hidden">
        <div ref={scrollRef} className="chat-scroll flex-1 overflow-y-auto py-4 space-y-5 min-w-0">
          {messages.length === 0 && (
            <div className="max-w-[820px] mx-auto px-4 text-center py-16">
              <div className="text-sm mb-2" style={{ color: 'var(--color-text)' }}>
                发送消息开始对话
              </div>
              <div className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
                @ 引用文件 · / 打开命令 · Enter 发送
                {onOpenFile ? ' · 左侧点文件可在右侧预览' : ''}
              </div>
            </div>
          )}

          {displayItems.map(item => {
            if (item.kind === 'process') {
              return (
                <div key={item.key} ref={bindRef()}>
                  <ProcessDetails
                    messages={item.messages}
                    toolCalls={item.toolCalls}
                    sessionId={sessionId}
                    forking={forking}
                    onFork={handleFork}
                  />
                </div>
              )
            }
            return (
              <div key={item.key} ref={bindRef()}>
                <MessageView
                  message={item.message}
                  sessionId={sessionId}
                  forking={forking === item.message.id}
                  onFork={handleFork}
                />
              </div>
            )
          })}

          {error && (
            <div className="max-w-[820px] mx-auto px-4">
              <div
                className="px-3 py-2 rounded-lg text-sm border"
                style={{
                  borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))',
                  backgroundColor: 'color-mix(in srgb, var(--color-danger) 8%, var(--color-bg))',
                  color: 'var(--color-danger)',
                }}
              >
                {error}
              </div>
            </div>
          )}

          {streaming && <div style={{ height: '20vh' }} />}
        </div>

        <ChatMinimap
          sources={minimapSources}
          scrollContainer={scrollRef}
          itemRefs={itemRefs}
        />
      </div>

      <div className="shrink-0 px-4 pb-3 pt-1" style={{ paddingRight: 52 }}>
        <div className="max-w-[820px] mx-auto">
          <ChatInput
            value={input}
            onChange={setInput}
            onSend={handleSend}
            onStop={stop}
            streaming={streaming}
            disabled={!canChat}
            placeholder={canChat ? '输入消息… /技能名 调用技能，@ 也可提技能或文件' : '请先配置服务商…'}
            workspaceId={workspaceId}
            onSlashCommand={handleSlashCommand}
            providers={ideMode ? providers : undefined}
            provider={provider}
            onProviderChange={ideMode ? setProvider : undefined}
          />
        </div>
      </div>
    </div>
  )
}
