/**
 * 过程详情折叠块 — 收起中间工具调用与草稿。
 * 关联：ChatView、MessageView。
 */
import { useState } from 'react'
import MessageView from './MessageView'
import type { ChatMessage } from '../../hooks/useChat'

interface ProcessDetailsProps {
  messages: ChatMessage[]
  toolCalls: number
  sessionId?: string
  forking?: string | null
  onFork?: (messageId: string) => void
}

export default function ProcessDetails({
  messages,
  toolCalls,
  sessionId,
  forking,
  onFork,
}: ProcessDetailsProps) {
  const [open, setOpen] = useState(false)
  const label = `过程详情 · ${messages.length} 条 · ${toolCalls} 次工具`

  return (
    <div className="max-w-[820px] mx-auto px-4 w-full">
      <button
        type="button"
        className="w-full text-left text-xs px-3 py-2 rounded-lg border flex items-center gap-2"
        style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-bg-secondary)',
          color: 'var(--color-text-secondary)',
        }}
        onClick={() => setOpen(o => !o)}
      >
        <span className="opacity-60">{open ? '▼' : '▶'}</span>
        <span>{label}</span>
      </button>
      {open && (
        <div className="mt-2 space-y-3 pl-1 border-l-2" style={{ borderColor: 'var(--color-border)' }}>
          {messages.map((m, i) => (
            <MessageView
              key={m.id ?? `proc-${i}`}
              message={m}
              sessionId={sessionId}
              forking={forking === m.id}
              onFork={onFork}
              compact
            />
          ))}
        </div>
      )}
    </div>
  )
}
