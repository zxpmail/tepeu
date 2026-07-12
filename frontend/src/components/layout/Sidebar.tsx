import type { Panel } from '../../types'

interface SidebarProps {
  panels: { id: Panel; label: string; icon: string }[]
  activePanel: Panel
  collapsed: boolean
  onSelect: (panel: Panel) => void
  onToggleCollapse: () => void
}

export default function Sidebar({ panels, activePanel, collapsed, onSelect, onToggleCollapse }: SidebarProps) {
  return (
    <nav
      className="flex flex-col border-r shrink-0 transition-all duration-200"
      style={{
        width: collapsed ? 48 : 200,
        borderColor: 'var(--color-border)',
        backgroundColor: 'var(--color-sidebar-bg)',
      }}
    >
      <div className="flex flex-col flex-1 py-2">
        {panels.map(p => (
          <button
            key={p.id}
            onClick={() => onSelect(p.id)}
            className="flex items-center gap-3 px-3 py-2 text-sm border-l-2 transition-colors hover:opacity-80"
            style={{
              borderLeftColor: activePanel === p.id ? 'var(--color-accent)' : 'transparent',
              color: activePanel === p.id ? 'var(--color-accent)' : 'var(--color-sidebar-text)',
              backgroundColor: activePanel === p.id ? 'var(--color-bg-tertiary)' : 'transparent',
            }}
            title={collapsed ? p.label : undefined}
          >
            <span className="text-lg shrink-0">{p.icon}</span>
            {!collapsed && <span className="truncate">{p.label}</span>}
          </button>
        ))}
      </div>
      <div className="p-2 border-t flex justify-center" style={{ borderColor: 'var(--color-border)' }}>
        <button
          onClick={onToggleCollapse}
          className="text-xs px-2 py-1 rounded hover:opacity-80"
          style={{ color: 'var(--color-text-secondary)' }}
        >
          {collapsed ? '→' : '←'}
        </button>
      </div>
    </nav>
  )
}
