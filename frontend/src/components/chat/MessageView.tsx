/**
 * 单条消息视图 — 用户 soft bubble / 助手 Markdown / 用量脚注 / 工具卡片。
 * 关联：ChatView、MarkdownBody、ProcessDetails。
 */
import { useState } from 'react'
import type { ChatMessage } from '../../hooks/useChat'
import MarkdownBody from './MarkdownBody'

interface MessageViewProps {
  message: ChatMessage
  sessionId?: string
  forking?: boolean
  onFork?: (messageId: string) => void
  /** 过程详情内嵌时略缩边距 */
  compact?: boolean
}

/** 工具调用/结果可折叠卡片 */
function ToolCard({ message }: { message: ChatMessage }) {
  const [open, setOpen] = useState(false)
  const isCall = message.toolKind === 'call'
  return (
    <div
      className="rounded-lg border text-xs overflow-hidden"
      style={{
        borderColor: isCall
          ? 'color-mix(in srgb, #22c55e 35%, var(--color-border))'
          : 'var(--color-border)',
        backgroundColor: 'var(--color-bg-secondary)',
      }}
    >
      <button
        type="button"
        className="w-full flex items-center gap-2 px-3 py-1.5 text-left"
        style={{ color: 'var(--color-text-secondary)', fontFamily: 'var(--font-mono)' }}
        onClick={() => setOpen(o => !o)}
      >
        <span className="opacity-60">{open ? '▼' : '▶'}</span>
        <span>{isCall ? 'call' : 'result'}</span>
        <span style={{ color: 'var(--color-text)' }}>{message.tool}</span>
      </button>
      {open && (
        <pre
          className="px-3 pb-2 m-0 whitespace-pre-wrap break-words max-h-48 overflow-auto"
          style={{
            fontFamily: 'var(--font-mono)',
            fontSize: 11,
            color: 'var(--color-text-dim)',
          }}
        >
          {message.content}
        </pre>
      )}
    </div>
  )
}

/** 复制文本到剪贴板 */
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    /* ignore */
  }
}

/** 格式化单条消息用量 */
function formatUsage(u: NonNullable<ChatMessage['usage']>): string {
  const parts = [`↑${u.promptTokens}`, `↓${u.completionTokens}`]
  if (u.costUsd > 0) parts.push(`$${u.costUsd.toFixed(4)}`)
  return parts.join(' · ')
}

export default function MessageView({
  message,
  sessionId,
  forking,
  onFork,
  compact = false,
}: MessageViewProps) {
  const wrapClass = compact
    ? 'w-full px-2'
    : 'group max-w-[820px] mx-auto px-4 w-full'

  if (message.role === 'tool') {
    return (
      <div className={wrapClass}>
        <ToolCard message={message} />
      </div>
    )
  }

  const isUser = message.role === 'user'

  return (
    <div className={`${wrapClass} flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div className={`relative ${isUser ? 'max-w-[85%]' : 'w-full'}`}>
        {isUser ? (
          <div
            className="px-3.5 py-2.5 rounded-xl text-sm"
            style={{
              backgroundColor: 'var(--color-user-bg)',
              border: '1px solid color-mix(in srgb, var(--color-accent) 22%, var(--color-border))',
              color: 'var(--color-text)',
            }}
          >
            <MarkdownBody content={message.content} className="markdown-user-message" />
          </div>
        ) : (
          <div className="text-sm py-1">
            {!compact && (
              <div className="text-[11px] mb-1" style={{ color: 'var(--color-text-dim)' }}>
                Assistant
              </div>
            )}
            <MarkdownBody content={message.content} />
            {message.streaming && (
              <span className="inline-block animate-pulse ml-0.5" style={{ color: 'var(--color-accent)' }}>
                ▋
              </span>
            )}
            {!message.streaming && message.usage && (
              <div className="mt-2 text-[11px]" style={{ color: 'var(--color-text-dim)', fontFamily: 'var(--font-mono)' }}>
                {formatUsage(message.usage)}
              </div>
            )}
          </div>
        )}

        <div
          className="flex gap-1 mt-1 opacity-0 group-hover:opacity-100 transition-opacity"
          style={{ justifyContent: isUser ? 'flex-end' : 'flex-start' }}
        >
          <button
            type="button"
            className="text-[10px] px-1.5 py-0.5 rounded"
            style={{ color: 'var(--color-text-dim)', backgroundColor: 'var(--color-bg-secondary)' }}
            onClick={() => void copyText(message.content)}
          >
            复制
          </button>
          {message.id && sessionId && onFork && (
            <button
              type="button"
              className="text-[10px] px-1.5 py-0.5 rounded disabled:opacity-50"
              style={{ color: 'var(--color-text-dim)', backgroundColor: 'var(--color-bg-secondary)' }}
              disabled={forking}
              onClick={() => onFork(message.id!)}
            >
              {forking ? '分支中…' : '从此分支'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
