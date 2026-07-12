import { useTerminal } from '../../hooks/useTerminal'
import 'xterm/css/xterm.css'

export default function TerminalView() {
  const { terminalRef, connected, aiInput, aiSuggestion, setAiInput, translateAndExec } = useTerminal()

  return (
    <div className="flex flex-col h-full">
      {/* Connection status */}
      <div className="flex items-center gap-2 px-3 py-1.5 border-b text-xs shrink-0"
        style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}>
        <span className={`inline-block w-2 h-2 rounded-full ${connected ? 'bg-green-500' : 'bg-red-500'}`} />
        <span style={{ color: 'var(--color-text-secondary)' }}>
          {connected ? '已连接' : '已断开'}
        </span>
        <span className="ml-auto" style={{ color: 'var(--color-text-secondary)' }}>Windows 终端</span>
      </div>

      {/* Terminal */}
      <div ref={terminalRef as React.RefObject<HTMLDivElement>} className="flex-1" style={{ backgroundColor: '#1a1a1a' }} />

      {/* AI CLI helper */}
      <div className="p-2 border-t shrink-0" style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}>
        <div className="flex gap-2 items-end">
          <div className="flex-1">
            <div className="text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
              🤖 AI 命令行 — 描述你想做什么：
            </div>
            <div className="flex gap-2">
              <input
                value={aiInput}
                onChange={e => setAiInput(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') translateAndExec() }}
                placeholder='例如：列出文件、清屏，或直接输入 dir / cls'
                className="flex-1 px-3 py-1.5 text-sm rounded border font-mono"
                style={{
                  borderColor: 'var(--color-border)',
                  backgroundColor: 'var(--color-bg)',
                  color: 'var(--color-text)',
                }}
              />
              <button
                onClick={translateAndExec}
                disabled={!aiInput.trim()}
                className="px-3 py-1.5 text-sm rounded disabled:opacity-50 whitespace-nowrap"
                style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
              >
                ⚡ 执行
              </button>
            </div>
          </div>
        </div>
        {aiSuggestion && (
          <div className="mt-1 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
            ↪ {aiSuggestion}
          </div>
        )}
      </div>
    </div>
  )
}
