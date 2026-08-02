/**
 * PPTX 在线预览 — 按容器自适应尺寸；支持浏览器原生全屏。
 * 关联：RightFilePanel、api.rawFileUrl；依赖 pptx-preview。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { init } from 'pptx-preview'

type Previewer = ReturnType<typeof init>

interface PptxPreviewProps {
  /** 可 fetch 的原始文件 URL（同域 /api/files/raw） */
  src: string
  /** 文件名，仅用于错误提示 */
  filename?: string
  /** 路径变化时强制重载 */
  reloadKey?: number
}

/** ZIP/PPTX 本地文件头：PK\x03\x04 或空 ZIP PK\x05\x06 */
function looksLikeZip(buf: ArrayBuffer): boolean {
  if (buf.byteLength < 4) return false
  const u8 = new Uint8Array(buf)
  return u8[0] === 0x50 && u8[1] === 0x4b && (u8[2] === 0x03 || u8[2] === 0x05 || u8[2] === 0x07)
}

/** 把 jszip 等英文错误改成可读说明 */
function humanizePreviewError(e: unknown): string {
  const raw = e instanceof Error ? e.message : String(e)
  if (/end of central directory|is this a zip file/i.test(raw)) {
    return '无法预览：文件不是完整的 PPTX（常见于占位文件或写入未完成）。请重新生成后再打开。'
  }
  return raw || 'PPTX 预览失败'
}

/** 在容器内按 16:9 尽量铺满（contain） */
function fitSize(boxW: number, boxH: number): { width: number; height: number } {
  const w = Math.max(280, boxW)
  const h = Math.max(160, boxH)
  let width = w
  let height = Math.round(w * 9 / 16)
  if (height > h) {
    height = h
    width = Math.round(h * 16 / 9)
  }
  return { width, height }
}

export default function PptxPreview({ src, filename, reloadKey = 0 }: PptxPreviewProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const hostRef = useRef<HTMLDivElement>(null)
  const bufRef = useRef<ArrayBuffer | null>(null)
  const previewerRef = useRef<Previewer | null>(null)
  const sizeRef = useRef({ width: 0, height: 0 })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [isFs, setIsFs] = useState(false)

  const destroyPreviewer = useCallback(() => {
    try {
      previewerRef.current?.destroy()
    } catch { /* ignore */ }
    previewerRef.current = null
    const host = hostRef.current
    if (host) host.innerHTML = ''
  }, [])

  const paint = useCallback(async (buf: ArrayBuffer) => {
    const root = rootRef.current
    const host = hostRef.current
    if (!root || !host) return

    const boxW = root.clientWidth - 8
    const boxH = root.clientHeight - 40 // 预留给底栏全屏按钮
    const { width, height } = fitSize(boxW, boxH)
    if (width < 40 || height < 40) return

    // 尺寸变化不大则跳过，避免翻页时抖动
    const prev = sizeRef.current
    if (
      previewerRef.current
      && Math.abs(prev.width - width) < 24
      && Math.abs(prev.height - height) < 24
    ) {
      return
    }

    destroyPreviewer()
    sizeRef.current = { width, height }
    const previewer = init(host, { width, height, mode: 'slide' })
    previewerRef.current = previewer
    await previewer.preview(buf)
  }, [destroyPreviewer])

  useEffect(() => {
    let cancelled = false
    bufRef.current = null
    destroyPreviewer()
    setLoading(true)
    setError(null)

    const run = async () => {
      try {
        const res = await fetch(src)
        if (!res.ok) throw new Error(`加载失败（HTTP ${res.status}）`)
        const buf = await res.arrayBuffer()
        if (cancelled) return

        if (!looksLikeZip(buf)) {
          const hint = buf.byteLength < 64
            ? '文件过小，多半是生成时写入的占位内容，不是完整幻灯片。'
            : '内容不像 PPTX（ZIP）格式，可能已损坏或扩展名不符。'
          throw new Error(`无法预览：${hint}请重新生成 .pptx，或改用下载。`)
        }

        bufRef.current = buf
        // 等一帧让容器完成布局，再量尺寸
        await new Promise<void>(r => requestAnimationFrame(() => r()))
        if (cancelled) return
        await paint(buf)
      } catch (e) {
        if (!cancelled) setError(humanizePreviewError(e))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
      destroyPreviewer()
      bufRef.current = null
    }
  }, [src, reloadKey, paint, destroyPreviewer])

  // 加载结束后强制按最终布局重测一次（去掉「加载中」后高度会变大）
  useEffect(() => {
    if (loading || error) return
    const buf = bufRef.current
    if (!buf) return
    sizeRef.current = { width: 0, height: 0 }
    void paint(buf).catch(() => {})
  }, [loading, error, paint])

  // 容器尺寸变化（含面板全屏 / 浏览器全屏）时重绘
  useEffect(() => {
    const root = rootRef.current
    if (!root) return
    let timer: ReturnType<typeof setTimeout> | null = null
    const ro = new ResizeObserver(() => {
      if (timer) clearTimeout(timer)
      timer = setTimeout(() => {
        const buf = bufRef.current
        if (buf) void paint(buf).catch(() => {})
      }, 120)
    })
    ro.observe(root)
    return () => {
      if (timer) clearTimeout(timer)
      ro.disconnect()
    }
  }, [paint])

  useEffect(() => {
    const onFs = () => setIsFs(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', onFs)
    return () => document.removeEventListener('fullscreenchange', onFs)
  }, [])

  const toggleFullscreen = async () => {
    const root = rootRef.current
    if (!root) return
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen()
      } else {
        await root.requestFullscreen()
      }
    } catch {
      // 个别环境禁用 Fullscreen API 时忽略
    }
  }

  return (
    <div
      ref={rootRef}
      className="h-full w-full flex flex-col min-h-0"
      style={{ backgroundColor: isFs ? '#111' : 'var(--color-bg)' }}
      data-testid="pptx-preview"
    >
      {loading && (
        <div className="p-3 text-sm" style={{ color: 'var(--color-text-dim)' }}>
          正在打开幻灯片{filename ? `：${filename}` : '…'}
        </div>
      )}
      {error && (
        <div className="p-3 text-sm space-y-2" style={{ color: 'var(--color-danger)' }}>
          <div>{error}</div>
          <div className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
            有效的 .pptx 可在线翻页预览；旧版 .ppt、空占位文件或未写完的文件请下载或重新生成。
          </div>
        </div>
      )}

      <div
        ref={hostRef}
        className="flex-1 min-h-0 overflow-hidden flex items-center justify-center"
        style={{ display: error ? 'none' : undefined }}
      />

      {!error && (
        <div
          className="shrink-0 h-10 flex items-center justify-end gap-2 px-3 border-t"
          style={{
            borderColor: isFs ? 'rgba(255,255,255,0.12)' : 'var(--color-border)',
            backgroundColor: isFs ? 'rgba(0,0,0,0.55)' : 'var(--color-bg-secondary)',
          }}
        >
          <button
            type="button"
            data-testid="pptx-fullscreen"
            onClick={() => void toggleFullscreen()}
            className="text-sm px-3 py-1.5 rounded"
            style={{
              backgroundColor: 'var(--color-accent)',
              color: '#fff',
            }}
            title={isFs ? '退出全屏 (Esc)' : '全屏播放'}
          >
            {isFs ? '退出全屏' : '全屏'}
          </button>
        </div>
      )}
    </div>
  )
}
