import type { Theme } from '../../types'

interface ThemeToggleProps {
  theme: Theme
  onToggle: (theme: Theme) => void
}

export default function ThemeToggle({ theme, onToggle }: ThemeToggleProps) {
  const cycle = () => {
    if (theme === 'light') onToggle('dark')
    else if (theme === 'dark') onToggle('system')
    else onToggle('light')
  }

  const icon = theme === 'light' ? '☀️' : theme === 'dark' ? '🌙' : '💻'

  return (
    <button
      onClick={cycle}
      className="text-sm px-2 py-1 rounded hover:opacity-80"
      style={{ color: 'var(--color-text-secondary)' }}
      title={`主题：${theme === 'light' ? '浅色' : theme === 'dark' ? '深色' : '跟随系统'}`}
    >
      {icon}
    </button>
  )
}
