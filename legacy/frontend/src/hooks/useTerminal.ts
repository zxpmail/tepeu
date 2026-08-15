/**
 * 终端 WebSocket 连接与 AI 命令翻译。
 * 关联：TerminalView、后端 TerminalWebSocketHandler、instanceToken、ApprovalBanner。
 * 输入按行缓冲：本地回显，回车后才发给后端执行；危险命令经审批后继续。
 */
import { useEffect, useRef, useCallback, useState } from 'react'
import { api } from '../api/client'
import type { PendingApproval } from '../components/chat/ApprovalBanner'
import { ensureInstanceToken, getInstanceTokenSync } from '../security/instanceToken'

export interface UseTerminalReturn {
  terminalRef: React.RefObject<HTMLDivElement | null>
  connected: boolean
  aiInput: string
  aiSuggestion: string | null
  /** 最近一次命令失败的白话解释 */
  errorHint: string | null
  setAiInput: (v: string) => void
  translateAndExec: () => void
  pendingApprovals: PendingApproval[]
  decidingId: string | null
  decideApproval: (approvalId: string, decision: 'approve' | 'deny') => Promise<void>
}

/** 根据终端错误输出给出简短解释（非 LLM） */
function explainShellError(msg: string): string | null {
  if (!msg) return null
  if (/不是内部或外部命令|not recognized as an internal|command not found/i.test(msg)) {
    return '命令未找到：请检查拼写。Windows 下用 dir / type / cd，而不是 ls / cat / pwd。'
  }
  if (/找不到文件|cannot find|No such file|系统找不到指定的/i.test(msg)) {
    return '路径不存在：先用 dir 确认目录与文件名是否正确。'
  }
  if (/拒绝访问|Access is denied|Permission denied/i.test(msg)) {
    return '权限不足：文件可能被占用，或当前目录无写入权限。'
  }
  if (/APPROVAL|DENIED|未获批准|命令被拒绝|Missing or invalid token/i.test(msg)) {
    return '被安全策略拦截：在上方审批条中批准，或改用不危险的命令。'
  }
  if (/语法不正确|syntax error|不正确的语法/i.test(msg)) {
    return '命令语法有误：检查引号、路径空格（可用双引号包裹路径）。'
  }
  return null
}

const WS_BASE = `ws://${location.hostname}:30141/api/terminal/ws`

const COMMAND_MAP: Record<string, string> = {
  'list files': 'dir',
  'list directory': 'dir',
  'show files': 'dir',
  'current directory': 'cd',
  'show current directory': 'dir',
  'where am i': 'cd',
  'print working directory': 'cd',
  'list current directory': 'dir',
  'clear screen': 'cls',
  'clear': 'cls',
  'date': 'date /t',
  'time': 'time /t',
  'show date': 'date /t',
  'show time': 'time /t',
  'whoami': 'whoami',
  'hostname': 'hostname',
  'ip config': 'ipconfig',
  'network config': 'ipconfig',
  'system info': 'systeminfo',
  'disk usage': 'wmic logicaldisk get size,freespace,caption',
  'process list': 'tasklist',
  'running processes': 'tasklist',
  '列出文件': 'dir',
  '显示文件': 'dir',
  '列目录': 'dir',
  '当前目录': 'cd',
  '显示当前目录': 'dir',
  '列出当前目录': 'dir',
  '我在哪': 'cd',
  '清屏': 'cls',
  '清除屏幕': 'cls',
  '清空': 'cls',
  '显示日期': 'date /t',
  '显示时间': 'time /t',
  '网络配置': 'ipconfig',
  'ip配置': 'ipconfig',
  '系统信息': 'systeminfo',
  '进程列表': 'tasklist',
  '运行进程': 'tasklist',
}

/** 判断是否为可直接执行的 shell 命令 */
function isDirectCommand(input: string): boolean {
  return /^(dir|cd|cls|type|del|mkdir|md|rd|rmdir|copy|move|ren|ipconfig|tasklist|whoami|hostname|systeminfo|echo|date|time|wmic|ping|netstat|set|ver|help)\b/i.test(input.trim())
}

/** 将自然语言或直通命令解析为可执行命令 */
function findCommand(nl: string): string | null {
  const trimmed = nl.trim()
  if (!trimmed) return null
  if (isDirectCommand(trimmed)) return trimmed

  const lower = trimmed.toLowerCase()
  if (COMMAND_MAP[lower]) return COMMAND_MAP[lower]
  if (COMMAND_MAP[trimmed]) return COMMAND_MAP[trimmed]

  for (const [key, cmd] of Object.entries(COMMAND_MAP)) {
    if (lower.includes(key) || trimmed.includes(key)) return cmd
  }

  const readMatch = trimmed.match(/^(?:show|read|cat|open|display|显示|打开|查看)\s+(.+)/i)
  if (readMatch) return `type ${readMatch[1]}`

  const createMatch = trimmed.match(/^(?:create|make|new|创建)\s+(.+)/i)
  if (createMatch) return `echo. > ${createMatch[1]}`

  const deleteMatch = trimmed.match(/^(?:delete|remove|del|rm|删除)\s+(.+)/i)
  if (deleteMatch) return `del ${deleteMatch[1]}`

  const cdMatch = trimmed.match(/^(?:go to|change to|cd|进入|切换到)\s+(.+)/i)
  if (cdMatch) return `cd ${cdMatch[1]}`

  const mkdirMatch = trimmed.match(/^(?:make directory|mkdir|create folder|新建文件夹|创建目录)\s+(.+)/i)
  if (mkdirMatch) return `mkdir ${mkdirMatch[1]}`

  return null
}

export function useTerminal(workspaceId?: string): UseTerminalReturn {
  const terminalRef = useRef<HTMLDivElement>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const xtermRef = useRef<import('xterm').Terminal | null>(null)
  const lineBufferRef = useRef('')
  const [connected, setConnected] = useState(false)
  const [aiInput, setAiInput] = useState('')
  const [aiSuggestion, setAiSuggestion] = useState<string | null>(null)
  const [errorHint, setErrorHint] = useState<string | null>(null)
  const [pendingApprovals, setPendingApprovals] = useState<PendingApproval[]>([])
  const [decidingId, setDecidingId] = useState<string | null>(null)

  /** 发送整行命令；未连接时给出明确提示 */
  const sendCommand = useCallback((line: string) => {
    const ws = wsRef.current
    const term = xtermRef.current
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      term?.write('\r\n\x1b[31m未连接，无法执行。请刷新页面后重试。\x1b[0m\r\n')
      return false
    }
    ws.send(line)
    return true
  }, [])

  const decideApproval = useCallback(async (approvalId: string, decision: 'approve' | 'deny') => {
    setDecidingId(approvalId)
    try {
      await api.decideApproval(approvalId, decision)
      setPendingApprovals(prev => prev.filter(p => p.approvalId !== approvalId))
      const term = xtermRef.current
      if (decision === 'approve') {
        term?.write('\r\n\x1b[32m已批准，命令继续执行…\x1b[0m\r\n')
      } else {
        term?.write('\r\n\x1b[31m已拒绝该命令\x1b[0m\r\n')
      }
    } catch (e) {
      xtermRef.current?.write(
        `\r\n\x1b[31m审批失败：${e instanceof Error ? e.message : 'unknown'}\x1b[0m\r\n`,
      )
    } finally {
      setDecidingId(null)
    }
  }, [])

  // 初始化 xterm 与 WebSocket（cancelled + 局部句柄，防止 StrictMode 竞态泄漏）
  useEffect(() => {
    let cancelled = false
    let ws: WebSocket | null = null
    let term: import('xterm').Terminal | null = null
    let handleResize: (() => void) | undefined

    const tearDown = () => {
      if (handleResize) {
        window.removeEventListener('resize', handleResize)
        handleResize = undefined
      }
      if (wsRef.current === ws) wsRef.current = null
      if (xtermRef.current === term) xtermRef.current = null
      try { ws?.close() } catch { /* ignore */ }
      try { term?.dispose() } catch { /* ignore */ }
      ws = null
      term = null
    }

    const init = async () => {
      await ensureInstanceToken()
      if (cancelled) return

      const { Terminal } = await import('xterm')
      const { FitAddon } = await import('xterm-addon-fit')
      if (cancelled) return

      const nextTerm = new Terminal({
        cursorBlink: true,
        cursorStyle: 'block',
        fontSize: 13,
        fontFamily: "'Cascadia Code', 'JetBrains Mono', 'Fira Code', 'Consolas', monospace",
        theme: {
          background: '#1a1a1a',
          foreground: '#00ff00',
          cursor: '#00ff00',
          selectionBackground: '#335533',
        },
        allowTransparency: false,
        cols: 80,
        rows: 24,
      })
      const fitAddon = new FitAddon()
      nextTerm.loadAddon(fitAddon)

      if (cancelled) {
        nextTerm.dispose()
        return
      }

      term = nextTerm
      xtermRef.current = nextTerm
      lineBufferRef.current = ''

      if (terminalRef.current) {
        nextTerm.open(terminalRef.current)
        fitAddon.fit()
      }

      const token = getInstanceTokenSync()
      const qs = new URLSearchParams()
      if (token) qs.set('token', token)
      if (workspaceId) qs.set('workspaceId', workspaceId)
      const q = qs.toString()
      const wsUrl = q ? `${WS_BASE}?${q}` : WS_BASE
      const nextWs = new WebSocket(wsUrl)
      if (cancelled) {
        nextWs.close()
        tearDown()
        return
      }
      ws = nextWs
      wsRef.current = nextWs

      nextWs.onopen = () => {
        if (cancelled || ws !== nextWs) return
        setConnected(true)
        nextTerm.write('\r\n\x1b[32m=== Terminal connected ===\x1b[0m\r\n')
      }
      nextWs.onclose = () => {
        if (wsRef.current === nextWs) {
          setConnected(false)
          wsRef.current = null
        }
        if (!cancelled) {
          nextTerm.write('\r\n\x1b[31m=== Terminal disconnected ===\x1b[0m\r\n')
        }
      }
      nextWs.onerror = () => {
        if (!cancelled) {
          nextTerm.write('\r\n\x1b[31m=== Connection error ===\x1b[0m\r\n')
        }
      }
      nextWs.onmessage = (event) => {
        if (cancelled || ws !== nextWs) return
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'output') {
            nextTerm.write(msg.data + '\r\n')
            const hint = explainShellError(String(msg.data ?? ''))
            if (hint) setErrorHint(hint)
          } else if (msg.type === 'error') {
            nextTerm.write(`\r\n\x1b[31m${msg.message}\x1b[0m\r\n`)
            const hint = explainShellError(String(msg.message ?? ''))
            if (hint) setErrorHint(hint)
            if (typeof msg.message === 'string'
                && (msg.message.includes('APPROVAL_TIMEOUT') || msg.message.includes('DENIED'))) {
              setPendingApprovals([])
            }
          } else if (msg.type === 'approval_required' && msg.approvalId) {
            nextTerm.write(
              `\r\n\x1b[33m需要批准命令：${msg.command || ''}\x1b[0m\r\n`,
            )
            setPendingApprovals(prev => {
              if (prev.some(p => p.approvalId === msg.approvalId)) return prev
              return [
                ...prev,
                {
                  approvalId: msg.approvalId,
                  tool: msg.tool || 'terminal_shell',
                  params: msg.command || '',
                  sessionId: msg.sessionId,
                },
              ]
            })
          }
        } catch {
          nextTerm.write(event.data + '\r\n')
        }
      }

      // 按行缓冲：始终通过 wsRef 发送，避免闭包拿到已关闭的 socket
      nextTerm.onData((data: string) => {
        for (const ch of data) {
          if (ch === '\r' || ch === '\n') {
            const line = lineBufferRef.current
            lineBufferRef.current = ''
            nextTerm.write('\r\n')
            const wsNow = wsRef.current
            if (wsNow?.readyState === WebSocket.OPEN) {
              wsNow.send(line)
            } else {
              nextTerm.write('\x1b[31m未连接，无法执行。请刷新页面后重试。\x1b[0m\r\n')
            }
          } else if (ch === '\x7f' || ch === '\b') {
            if (lineBufferRef.current.length > 0) {
              lineBufferRef.current = lineBufferRef.current.slice(0, -1)
              nextTerm.write('\b \b')
            }
          } else if (ch === '\x03') {
            lineBufferRef.current = ''
            nextTerm.write('^C\r\n')
          } else if (ch >= ' ' || ch === '\t') {
            lineBufferRef.current += ch
            nextTerm.write(ch)
          }
        }
      })

      handleResize = () => fitAddon.fit()
      if (cancelled) {
        tearDown()
        return
      }
      window.addEventListener('resize', handleResize)
    }

    void init()

    return () => {
      cancelled = true
      tearDown()
      setConnected(false)
    }
  }, [workspaceId])

  /** 翻译自然语言并执行命令 */
  const translateAndExec = useCallback(() => {
    const cmd = findCommand(aiInput)
    setErrorHint(null)
    if (cmd) {
      setAiSuggestion(cmd)
      xtermRef.current?.write(cmd + '\r\n')
      sendCommand(cmd)
      setAiInput('')
    } else {
      setAiSuggestion(`未找到匹配命令"${aiInput}"。试试：dir、列出文件、清屏、显示当前目录`)
    }
  }, [aiInput, sendCommand])

  return {
    terminalRef,
    connected,
    aiInput,
    aiSuggestion,
    errorHint,
    setAiInput,
    translateAndExec,
    pendingApprovals,
    decidingId,
    decideApproval,
  }
}
