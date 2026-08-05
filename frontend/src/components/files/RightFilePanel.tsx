/**
 * 右栏文件预览 — HTML/PDF/图片/Markdown/PPTX/代码高亮；版本历史；支持全屏与下载。
 * 关联：api.rawFileUrl、api.readFile、PptxPreview、VersionPanel、codeHighlight、IdeShell。
 */
import { useEffect, useState, useCallback } from 'react'
import type { ReactNode } from 'react'
import { api } from '../../api/client'
import { workspaceEventBus } from '../../context/WorkspaceEvents'
import { marked } from 'marked'
import { highlightCode } from '../../lib/codeHighlight'
import PptxPreview from './PptxPreview'
import VersionPanel from './VersionPanel'

interface RightFilePanelProps {
  path: string | null
  workspaceId?: string
  onClose: () => void
}

type PreviewKind = 'html' | 'pdf' | 'image' | 'markdown' | 'pptx' | 'ppt-legacy' | 'text'

function detectKind(filename: string): PreviewKind {
  const n = filename.toLowerCase()
  if (/\.(html|htm)$/.test(n)) return 'html'
  if (/\.pdf$/.test(n)) return 'pdf'
  if (/\.(png|jpe?g|gif|webp|svg|bmp)$/.test(n)) return 'image'
  if (/\.(md|mkd|markdown)$/.test(n)) return 'markdown'
  if (/\.pptx$/.test(n)) return 'pptx'
  if (/\.ppt$/.test(n)) return 'ppt-legacy'
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
  const [showVersions, setShowVersions] = useState(false)

  const name = path ? (path.split('/').pop() || path) : ''
  const kind = path ? detectKind(name) : 'text'
  const needsTextLoad = kind === 'markdown' || kind === 'text'
  const rawUrl = path ? api.rawFileUrl(path, workspaceId) : ''
  const downloadUrl = path ? api.rawFileUrl(path, workspaceId) + '&download=1' : ''

  useEffect(() => {
    setFullscreen(false)
    setShowVersions(false)
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
    return workspaceEventBus.subscribe((changed, eventWorkspaceId) => {
      if (eventWorkspaceId != null && eventWorkspaceId !== workspaceId) return
      if (!changed || changed === path || changed.endsWith(path) || path.endsWith(changed.replace(/^\//, ''))) {
        setReloadKey(k => k + 1)
      }
    })
  }, [path, workspaceId])

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
      className="preview-toolbar shrink-0 h-9 flex items-center gap-0.5 px-2 border-b"
      style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
    >
      <span className="text-xs truncate flex-1 px-1" style={{ color: 'var(--color-text)', fontFamily: 'var(--font-mono)' }}>
        {path}
      </span>
      <button
        type="button"
        title="版本历史"
        onClick={() => setShowVersions(v => !v)}
        className="preview-tool-btn text-[11px] px-2"
        style={{ color: showVersions ? 'var(--color-accent)' : 'var(--color-text)' }}
      >
        版本
      </button>
      <button
        type="button"
        title={fullscreen ? '退出全屏 (Esc)' : '面板全屏'}
        onClick={toggleFullscreen}
        className="preview-tool-btn text-[11px] px-2"
        style={{ color: 'var(--color-text)' }}
      >
        {fullscreen ? '退出' : '全屏'}
      </button>
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
      {showVersions && path ? (
        <div className="h-full p-3 overflow-auto">
          <VersionPanel
            path={path}
            workspaceId={workspaceId}
            onRestore={() => {
              setShowVersions(false)
              setReloadKey(k => k + 1)
            }}
            onClose={() => setShowVersions(false)}
          />
        </div>
      ) : (
        <>
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
        <PptxPreview
          key={`${rawUrl}-${reloadKey}`}
          src={rawUrl}
          filename={name}
          reloadKey={reloadKey}
        />
      )}


      {!loading && !error && kind === 'ppt-legacy' && (
        <div className="h-full flex flex-col items-center justify-center gap-3 p-6 text-center">
          <div className="text-sm" style={{ color: 'var(--color-text)' }}>
            旧版 .ppt 暂不支持在线预览
          </div>
          <div className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
            请下载后用 PowerPoint 打开，或另存为 .pptx 后再预览。
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
          className="m-0 h-full overflow-auto p-3 text-xs leading-relaxed hljs"
          style={{ fontFamily: 'var(--font-mono)' }}
          dangerouslySetInnerHTML={{ __html: highlightCode(content, name) }}
        />
      )}
        </>
      )}
    </div>
  )

  if (fullscreen) {
    return (
      <div
        data-testid="file-preview"
        className="fixed inset-0 z-50 flex flex-col"
        style={{ backgroundColor: 'var(--color-bg)' }}
      >
        {toolbar}
        {body}
      </div>
    )
  }

  return (
    <div data-testid="file-preview" className="h-full flex flex-col overflow-hidden">
      {toolbar}
      {body}
    </div>
  )
}
