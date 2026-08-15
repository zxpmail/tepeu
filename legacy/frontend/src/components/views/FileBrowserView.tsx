/**
 * 全屏文件管理器：列表、预览（高亮/Markdown/图片）、版本历史+DIFF、拖拽上传。
 * 关联：useFileBrowser、VersionPanel、codeHighlight、api。
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import { api, ApiError } from '../../api/client'
import { useWorkspaceEvents } from '../../context/WorkspaceEvents'
import FileTree from '../common/FileTree'
import PptxPreview from '../files/PptxPreview'
import VersionPanel from '../files/VersionPanel'
import { ensureHljsLanguages, detectLanguage } from '../../lib/codeHighlight'
import type { FileItem } from '../../types'
import { marked } from 'marked'

ensureHljsLanguages()

function isImageFile(filename: string): boolean {
  return /\.(png|jpg|jpeg|gif|webp|svg|bmp)$/i.test(filename)
}

function isMarkdownFile(filename: string): boolean {
  return /\.(md|mkd|markdown)$/i.test(filename)
}

function isPptxFile(filename: string): boolean {
  return /\.pptx$/i.test(filename)
}

interface FilePreviewProps {
  path: string
  content: string
  mimeType: string
  workspaceId?: string
  onClose: () => void
}

function FilePreview({ path, content, mimeType, onClose, workspaceId }: FilePreviewProps) {
  const filename = path.split('/').pop() || path
  const codeRef = useRef<HTMLElement>(null)
  const hljs = ensureHljsLanguages()

  useEffect(() => {
    if (codeRef.current) {
      const lang = detectLanguage(filename)
      if (lang) codeRef.current.dataset.lang = lang
      codeRef.current.removeAttribute('data-highlighted')
      hljs.highlightElement(codeRef.current)
    }
  }, [content, filename, hljs])

  if (isPptxFile(filename) || mimeType?.includes('presentationml')) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between mb-3 shrink-0">
          <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
          <button type="button" onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
        </div>
        <div className="flex-1 min-h-0">
          <PptxPreview src={api.rawFileUrl(path, workspaceId)} filename={filename} />
        </div>
      </div>
    )
  }

  if (mimeType?.startsWith('image/') || isImageFile(filename)) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between mb-3 shrink-0">
          <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
          <button type="button" onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
        </div>
        <div className="flex-1 flex items-center justify-center overflow-auto bg-[#f0f0f0] dark:bg-[#111] rounded">
          <img
            src={api.rawFileUrl(path, workspaceId)}
            alt={filename}
            className="max-w-full max-h-full object-contain"
          />
        </div>
      </div>
    )
  }

  if (mimeType?.includes('markdown') || isMarkdownFile(filename)) {
    const html = marked.parse(content) as string
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between mb-3 shrink-0">
          <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
          <button type="button" onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
        </div>
        <div
          className="flex-1 overflow-auto p-4 rounded prose prose-sm dark:prose-invert max-w-none"
          style={{ backgroundColor: 'var(--color-bg-secondary)', color: 'var(--color-text)' }}
          dangerouslySetInnerHTML={{ __html: html }}
        />
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
        <button type="button" onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
      </div>
      <pre className="flex-1 overflow-auto text-sm p-4 rounded" style={{ backgroundColor: 'var(--color-bg-secondary)' }}>
        <code ref={codeRef} className="hljs">{content}</code>
      </pre>
    </div>
  )
}

interface FileBrowserViewProps {
  fileBrowser: {
    files: FileItem[]
    currentPath: string
    loading: boolean
    error: string | null
    navigateTo: (path: string) => void
    loadFiles: (path?: string) => void
  }
  workspaceId?: string
}

export default function FileBrowserView({ fileBrowser, workspaceId }: FileBrowserViewProps) {
  const { files, currentPath, loading, error, navigateTo, loadFiles } = fileBrowser
  const { subscribe } = useWorkspaceEvents()
  const [previewFile, setPreviewFile] = useState<{ path: string; content: string; mimeType: string } | null>(null)
  const [showVersions, setShowVersions] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [treeRefreshKey, setTreeRefreshKey] = useState(0)
  const previewPathRef = useRef<string | null>(null)

  useEffect(() => {
    previewPathRef.current = previewFile?.path ?? null
  }, [previewFile?.path])

  useEffect(() => {
    return subscribe((path: string, eventWorkspaceId?: string) => {
      // 带 workspaceId 的事件必须匹配当前工作区；缺省视为当前工作区
      if (eventWorkspaceId != null && eventWorkspaceId !== workspaceId) return
      // 目录树已展开节点重载（Phase 12 外部文件变更自动反映）
      setTreeRefreshKey(k => k + 1)
      const current = previewPathRef.current
      if (!current) return
      const norm = (p: string) => p.replace(/\\/g, '/').replace(/^\/+/, '')
      if (norm(current) !== norm(path) && current !== path) return
      api.readFile(current, workspaceId)
        .then(data => setPreviewFile(data))
        .catch(() => { /* 刷新失败忽略 */ })
    })
  }, [subscribe, workspaceId])

  const handleItemClick = async (item: FileItem) => {
    if (item.isDirectory) {
      const newPath = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
      navigateTo(newPath)
    } else {
      try {
        setActionError(null)
        const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
        setShowVersions(null)
        if (isPptxFile(item.name)) {
          setPreviewFile({
            path,
            content: '',
            mimeType: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
          })
          return
        }
        if (isImageFile(item.name)) {
          setPreviewFile({ path, content: '', mimeType: 'image/*' })
          return
        }
        const data = await api.readFile(path, workspaceId)
        setPreviewFile(data)
      } catch (e) {
        setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : '打开文件失败')
      }
    }
  }

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
  }, [])

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
    const fileList = Array.from(e.dataTransfer.files)
    if (fileList.length === 0) return
    setUploading(true)
    setActionError(null)
    try {
      for (const file of fileList) {
        await api.uploadFile(file, currentPath, workspaceId)
      }
      loadFiles(currentPath)
      setTreeRefreshKey(k => k + 1)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }, [currentPath, loadFiles, workspaceId])

  if (loading) {
    return <div className="p-4" style={{ color: 'var(--color-text-secondary)' }}>加载中...</div>
  }

  if (error) {
    return (
      <div className="p-4">
        <div className="p-3 rounded text-sm border border-red-300 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300">
          {error}
        </div>
      </div>
    )
  }

  return (
    <div
      className="flex h-full relative"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <aside
        className="w-52 shrink-0 border-r overflow-auto p-2"
        style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
      >
        <div className="text-[10px] uppercase tracking-wide px-1 mb-1" style={{ color: 'var(--color-text-dim)' }}>
          目录树
        </div>
        <FileTree
          workspaceId={workspaceId}
          selectedPath={currentPath}
          refreshKey={treeRefreshKey}
          onSelectDir={(path) => {
            navigateTo(path)
            setPreviewFile(null)
            setShowVersions(null)
          }}
        />
      </aside>
      <div className="flex-1 p-4 overflow-auto relative" style={{ borderColor: 'var(--color-border)' }}>
        <div className="flex items-center gap-1 mb-3 text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          <button type="button" onClick={() => navigateTo('/')} className="hover:underline">~</button>
          {currentPath.split('/').filter(Boolean).map((segment, i, arr) => (
            <span key={i}>
              <span className="mx-1">/</span>
              <button
                type="button"
                onClick={() => {
                  const path = '/' + arr.slice(0, i + 1).join('/')
                  navigateTo(path)
                }}
                className="hover:underline"
              >
                {segment}
              </button>
            </span>
          ))}
        </div>

        {actionError && (
          <div className="mb-3 p-2 rounded text-xs" style={{ color: 'var(--color-danger)', backgroundColor: 'color-mix(in srgb, var(--color-danger) 10%, transparent)' }}>
            {actionError}
          </div>
        )}

        {files.length === 0 && (
          <div className="p-8 text-center text-sm" style={{ color: 'var(--color-text-secondary)' }}>
            空目录 — 可拖放文件到此处上传
          </div>
        )}

        <div className="space-y-0.5">
          <button
            type="button"
            onClick={() => {
              const parent = currentPath.split('/').slice(0, -1).join('/') || '/'
              navigateTo(parent)
            }}
            className="flex items-center gap-2 px-2 py-1 text-sm rounded hover:opacity-80 w-full text-left"
            style={{ color: 'var(--color-text-secondary)' }}
            disabled={currentPath === '/'}
          >
            📁 ..
          </button>
          {files.map((item, i) => (
            <div key={i} className="flex items-center gap-1 group">
              <button
                type="button"
                onClick={() => void handleItemClick(item)}
                className="flex items-center gap-2 px-2 py-1 text-sm rounded hover:opacity-80 flex-1 text-left"
                style={{ color: 'var(--color-text)' }}
              >
                <span>{item.isDirectory ? '📁' : '📄'}</span>
                <span className="truncate flex-1">{item.name}</span>
                {!item.isDirectory && (
                  <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                    {item.size > 1024 ? `${(item.size / 1024).toFixed(1)} KB` : `${item.size} B`}
                  </span>
                )}
              </button>
              {!item.isDirectory && (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
                      setPreviewFile(null)
                      setShowVersions(showVersions === path ? null : path)
                    }}
                    className="text-xs px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity"
                    style={{ color: 'var(--color-text-secondary)' }}
                    title="版本历史"
                  >
                    🕐
                  </button>
                  <button
                    type="button"
                    data-testid="file-delete"
                    onClick={() => {
                      const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
                      void (async () => {
                        if (!window.confirm(`确定删除 ${item.name}？`)) return
                        try {
                          setActionError(null)
                          await api.deleteFile(path, workspaceId)
                          if (previewFile?.path === path) setPreviewFile(null)
                          if (showVersions === path) setShowVersions(null)
                          loadFiles(currentPath)
                          setTreeRefreshKey(k => k + 1)
                        } catch (e) {
                          setActionError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : '删除失败')
                        }
                      })()
                    }}
                    className="text-xs px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity"
                    style={{ color: 'var(--color-danger)' }}
                    title="删除文件"
                  >
                    ✕
                  </button>
                </>
              )}
            </div>
          ))}
        </div>

        {dragOver && (
          <div className="absolute inset-0 rounded-lg border-2 border-dashed z-10 flex items-center justify-center"
            style={{
              borderColor: 'var(--color-accent)',
              backgroundColor: 'rgba(37, 99, 235, 0.08)',
            }}
          >
            <div className="text-center">
              <div className="text-2xl mb-2">📁</div>
              <div className="text-sm font-medium" style={{ color: 'var(--color-accent)' }}>
                {uploading ? '上传中…' : '拖放文件到此处上传'}
              </div>
            </div>
          </div>
        )}
      </div>

      {(previewFile || showVersions) && (
        <div className="w-1/2 border-l overflow-auto p-4" style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-panel-bg)',
        }}>
          {previewFile && !showVersions && (
            <FilePreview
              path={previewFile.path}
              content={previewFile.content}
              mimeType={previewFile.mimeType}
              workspaceId={workspaceId}
              onClose={() => setPreviewFile(null)}
            />
          )}
          {showVersions && (
            <VersionPanel
              path={showVersions}
              workspaceId={workspaceId}
              onRestore={() => { setShowVersions(null); loadFiles(currentPath) }}
              onClose={() => setShowVersions(null)}
            />
          )}
        </div>
      )}
    </div>
  )
}
