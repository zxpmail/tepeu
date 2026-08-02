/**
 * 高危工具审批条 — Approve / Deny（Spec M2.3）。
 * 关联：useChat pendingApprovals、api.decideApproval。
 */
export interface PendingApproval {
  approvalId: string
  tool: string
  params: string
  sessionId?: string
}

interface ApprovalBannerProps {
  items: PendingApproval[]
  decidingId: string | null
  onDecide: (approvalId: string, decision: 'approve' | 'deny') => void
}

export default function ApprovalBanner({ items, decidingId, onDecide }: ApprovalBannerProps) {
  if (items.length === 0) return null

  return (
    <div className="space-y-2 mb-2">
      {items.map(item => (
        <div
          key={item.approvalId}
          className="rounded-lg border px-3 py-2 text-sm"
          style={{
            borderColor: 'var(--color-border)',
            backgroundColor: 'var(--color-bg-secondary)',
            color: 'var(--color-text)',
          }}
        >
          <div className="font-medium mb-1">
            需要批准：<code className="text-xs">{item.tool}</code>
          </div>
          {item.params && (
            <pre
              className="text-[11px] mb-2 max-h-20 overflow-auto whitespace-pre-wrap break-all"
              style={{ color: 'var(--color-text-dim)' }}
            >
              {item.params.length > 400 ? item.params.slice(0, 400) + '…' : item.params}
            </pre>
          )}
          <div className="flex gap-2">
            <button
              type="button"
              disabled={decidingId === item.approvalId}
              onClick={() => onDecide(item.approvalId, 'approve')}
              className="px-3 py-1 rounded text-xs"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >
              批准
            </button>
            <button
              type="button"
              disabled={decidingId === item.approvalId}
              onClick={() => onDecide(item.approvalId, 'deny')}
              className="px-3 py-1 rounded text-xs"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              拒绝
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
