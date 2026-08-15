/**
 * 简易行级差异对比（版本 / 当前文件）。
 * 关联：VersionPanel、FileBrowserView。
 */

export type DiffLine = { type: 'same' | 'add' | 'del'; text: string; oldNo?: number; newNo?: number }

/** 基于 LCS 的行 diff，输出统一风格行列表 */
export function computeLineDiff(oldText: string, newText: string): DiffLine[] {
  const a = oldText.replace(/\r\n/g, '\n').split('\n')
  const b = newText.replace(/\r\n/g, '\n').split('\n')
  const n = a.length
  const m = b.length
  const dp: number[][] = Array.from({ length: n + 1 }, () => Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i]![j] = a[i] === b[j] ? (dp[i + 1]![j + 1]! + 1) : Math.max(dp[i + 1]![j]!, dp[i]![j + 1]!)
    }
  }
  const out: DiffLine[] = []
  let i = 0
  let j = 0
  let oldNo = 1
  let newNo = 1
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ type: 'same', text: a[i]!, oldNo: oldNo++, newNo: newNo++ })
      i++
      j++
    } else if (dp[i + 1]![j]! >= dp[i]![j + 1]!) {
      out.push({ type: 'del', text: a[i]!, oldNo: oldNo++ })
      i++
    } else {
      out.push({ type: 'add', text: b[j]!, newNo: newNo++ })
      j++
    }
  }
  while (i < n) out.push({ type: 'del', text: a[i++]!, oldNo: oldNo++ })
  while (j < m) out.push({ type: 'add', text: b[j++]!, newNo: newNo++ })
  return out
}

interface FileDiffProps {
  oldLabel: string
  newLabel: string
  oldText: string
  newText: string
}

export default function FileDiff({ oldLabel, newLabel, oldText, newText }: FileDiffProps) {
  const lines = computeLineDiff(oldText, newText)
  const changed = lines.filter(l => l.type !== 'same').length

  return (
    <div className="flex flex-col h-full min-h-0">
      <div className="text-xs mb-2 shrink-0" style={{ color: 'var(--color-text-secondary)' }}>
        对比：{oldLabel} → {newLabel}
        {changed === 0 ? '（无差异）' : `（${changed} 行变更）`}
      </div>
      <pre
        className="flex-1 overflow-auto text-[11px] leading-5 p-2 rounded font-mono"
        style={{ backgroundColor: 'var(--color-bg-secondary)', color: 'var(--color-text)' }}
      >
        {lines.map((l, idx) => {
          const bg =
            l.type === 'add' ? 'rgba(34,197,94,0.15)'
              : l.type === 'del' ? 'rgba(239,68,68,0.15)'
                : 'transparent'
          const prefix = l.type === 'add' ? '+' : l.type === 'del' ? '-' : ' '
          return (
            <div key={idx} style={{ backgroundColor: bg }}>
              <span style={{ color: 'var(--color-text-dim)', userSelect: 'none' }}>{prefix} </span>
              {l.text || ' '}
            </div>
          )
        })}
      </pre>
    </div>
  )
}
