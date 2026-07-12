/**
 * 右栏文件预览 — HTML/PDF/图片/Markdown 渲染；支持全屏与下载（去掉源码切换）。
 * 关联：api.rawFileUrl、api.readFile、IdeShell、workspaceEventBus。
 */
import { useEffect, useState, useCallback } from 'react'
import type { ReactNode } from 'react'
import { api } from '../../api/client'
import { workspaceEventBus } from '../../context/WorkspaceEvents'
import { marked } from 'marked'

interface RightFilePanelProps {
  path: string | null
  workspaceId?: string
  onClose: () => void
}

type PreviewKind = 'html' | 'pdf' | 'image' | 'markdown' | 'pptx' | 'text'

function detectKind(filename: string): PreviewKind {
  const n = filename.toLowerCase()
  if (/\.(html|htm)$/.test(n)) return 'html'
  if (/\.pdf$/.test(n)) return 'pdf'
  if (/\.(png|jpe?g|gif|webp|svg|bmp)$/.test(n)) return 'image'
  if (/\.(md|mkd|markdown)$/.test(n)) return 'markdown'
  if (/\.(pptx|ppt)$/.test(n)) return 'pptx'
  return 'text'
}

/** 预览顶栏小图标按钮 */
function ToolIcon({
  title,
  onClick,
  href,
  download,
  children,
}: {
  title: string
  onClick?: () => void
  href?: string
  download?: string
  children: ReactNode
}) {
  const className = 'preview-tool-btn'
  const style = { color: 'var(--color-text-dim)' as const }
  if (href) {
    return (
      <a href={href} download={download} title={title} className={className} style={style}>
        {children}
      </a>
    )
  }
  return (
    <button type="button" title={title} onClick={onClick} className={className} style={style}>
      {children}
    </button>
  )
}

function IconExpand() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
    </svg>
  )
}

function IconCompress() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7" />
    </svg>
  )
}

function IconDownload() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 3v12M7 10l5 5 5-5" />
      <path d="M5 21h14" />
    </svg>
  )
}

function IconClose() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  )
}

export default function RightFilePanel({ path, workspaceId, onClose }: RightFilePanelProps) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [fullscreen, setFullscreen] = useState(false)

  const name = path ? (path.split('/').pop() || path) : ''
  const kind = path ? detectKind(name) : 'text'
  const needsTextLoad = kind === 'markdown' || kind === 'text'
  const rawUrl = path ? api.rawFileUrl(path, workspaceId) : ''
  const downloadUrl = path ? api.rawFileUrl(path, workspaceId) + '&download=1' : ''

  useEffect(() => {
    setFullscreen(false)
  }, [path])

  useEffect(() => {
    if (!fullscreen) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setFullscreen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [fullscreen])

  useEffect(() => {
    if (!path || !needsTextLoad) {
      setContent('')
      setError(null)
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    api.readFile(path, workspaceId)
      .then(data => {
        if (cancelled) return
        setContent(data.content)
      })
      .catch(e => {
        if (cancelled) return
        setError(e instanceof Error ? e.message : '读取失败')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
  }, [path, workspaceId, needsTextLoad, reloadKey])

  useEffect(() => {
    if (!path) return
    return workspaceEventBus.subscribe((changed) => {
      if (!changed || changed === path || changed.endsWith(path) || path.endsWith(changed.replace(/^\//, ''))) {
        setReloadKey(k => k + 1)
      }
    })
  }, [path])

  const toggleFullscreen = useCallback(() => setFullscreen(f => !f), [])

  if (!path) {
    return (
      <div
        className="h-full flex items-center justify-center text-xs"
        style={{ color: 'var(--color-text-dim)' }}
      >
        No file open
      </div>
    )
  }

  const toolbar = (
    <div
      className="shrink-0 h-9 flex items-center gap-0.5 px-2 border-b"
      style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
    >
      <span className="text-xs truncate flex-1 px-1" style={{ color: 'var(--color-text)', fontFamily: 'var(--font-mono)' }}>
        {path}
      </span>
      <ToolIcon title={fullscreen ? '退出全屏 (Esc)' : '全屏'} onClick={toggleFullscreen}>
        {fullscreen ? <IconCompress /> : <IconExpand />}
      </ToolIcon>
      <ToolIcon title="下载" href={downloadUrl} download={name}>
        <IconDownload />
      </ToolIcon>
      {!fullscreen && (
        <ToolIcon title="关闭" onClick={onClose}>
          <IconClose />
        </ToolIcon>
      )}
    </div>
  )

  const body = (
    <div className="flex-1 min-h-0 overflow-hidden" style={{ color: 'var(--color-text)' }}>
      {loading && (
        <div className="p-3 text-sm" style={{ color: 'var(--color-text-dim)' }}>加载中…</div>
      )}
      {error && (
        <div className="p-3 text-sm" style={{ color: 'var(--color-danger)' }}>{error}</div>
      )}

      {!loading && !error && kind === 'html' && (
        <iframe
          key={`${rawUrl}-${reloadKey}`}
          title={name}
          src={rawUrl}
          className="w-full h-full border-0 bg-white"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
        />
      )}

      {!loading && !error && kind === 'pdf' && (
        <iframe
          key={`${rawUrl}-${reloadKey}`}
          title={name}
          src={rawUrl}
          className="w-full h-full border-0"
        />
      )}

      {!loading && !error && kind === 'image' && (
        <div className="h-full overflow-auto flex items-center justify-center p-2" style={{ backgroundColor: 'var(--color-bg)' }}>
          <img
            key={`${rawUrl}-${reloadKey}`}
            src={rawUrl}
            alt={name}
            className="max-w-full max-h-full object-contain"
          />
        </div>
      )}

      {!loading && !error && kind === 'markdown' && (
        <div
          className="h-full overflow-auto p-3 markdown-body prose prose-sm max-w-none"
          dangerouslySetInnerHTML={{ __html: marked.parse(content) as string }}
        />
      )}

      {!loading && !error && kind === 'pptx' && (
        <div className="h-full flex flex-col items-center justify-center gap-3 p-6 text-center">
          <div className="text-sm" style={{ color: 'var(--color-text)' }}>
            浏览器无法直接预览 PowerPoint 文件
          </div>
          <div className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
            请下载后用本地软件打开；HTML 幻灯片可直接预览。
          </div>
          <a
            href={downloadUrl}
            download={name}
            className="text-sm px-3 py-1.5 rounded"
            style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
          >
            下载 {name}
          </a>
        </div>
      )}

      {!loading && !error && kind === 'text' && (
        <pre
          className="m-0 h-full overflow-auto p-3 whitespace-pre-wrap break-words text-xs leading-relaxed"
          style={{ fontFamily: 'var(--font-mono)' }}
        >
          {content}
        </pre>
      )}
    </div>
  )

  if (fullscreen) {
    return (
      <div
        className="fixed inset-0 z-50 flex flex-col"
        style={{ backgroundColor: 'var(--color-bg)' }}
      >
        {toolbar}
        {body}
      </div>
    )
  }

  return (
    <div className="h-full flex flex-col overflow-hidden">
      {toolbar}
      {body}
    </div>
  )
}
