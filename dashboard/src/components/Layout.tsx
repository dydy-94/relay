import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  MessageSquare,
  Bot,
  Users,
  LogOut,
  Radio,
  Server,
  Sun,
  Moon,
} from 'lucide-react'
import { getSession, clearSession } from '@/api/client'
import { cn } from '@/lib/utils'
import { useTheme } from '@/hooks/useTheme'

const NAV = [
  { to: '/overview', label: '总览', icon: LayoutDashboard },
  { to: '/channels', label: '频道', icon: MessageSquare },
  { to: '/agents', label: 'Agent', icon: Bot },
  { to: '/accounts', label: '账号', icon: Users },
]

export default function Layout() {
  const navigate = useNavigate()
  const [session, setSession] = useState(() => getSession())
  const { theme, toggle } = useTheme()

  useEffect(() => {
    const onStorage = () => setSession(getSession())
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const logout = () => {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="mission-bg flex h-screen overflow-hidden">
      <div className="top-beam" />
      {/* 侧边导航 */}
      <aside className="sidebar-panel flex w-56 shrink-0 flex-col">
        <div className="flex items-center gap-3 px-5 pb-5 pt-6">
          <div className="relative flex h-9 w-9 items-center justify-center rounded-xl border border-accent/40 bg-accent/10">
            <Radio className="h-5 w-5 text-accent" />
            <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-ok animate-breathe" />
          </div>
          <div>
            <p className="font-display text-sm font-bold leading-tight tracking-wide text-slate-100">
              ACP Relay
            </p>
            <p className="text-[10px] uppercase tracking-[0.22em] text-slate-500">
              Mission Control
            </p>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all',
                  isActive
                    ? 'bg-accent/10 text-accent-soft shadow-glow-sm'
                    : 'text-slate-400 hover:bg-white/[0.04] hover:text-slate-200',
                )
              }
            >
              <Icon className="h-[18px] w-[18px]" />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* 底部：主题切换 + 当前账号 + 实例信息 */}
        <div className="side-divider border-t p-4">
          <button
            onClick={toggle}
            title={theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'}
            className="side-surface mb-3 flex w-full items-center gap-2.5 rounded-lg border bg-white/[0.02] px-3 py-2 text-sm text-slate-400 transition-colors hover:border-accent/40 hover:text-slate-200"
          >
            <span className="flex h-6 w-6 items-center justify-center rounded-md bg-accent/10">
              {theme === 'dark' ? (
                <Sun className="h-3.5 w-3.5 text-accent" />
              ) : (
                <Moon className="h-3.5 w-3.5 text-accent" />
              )}
            </span>
            {theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'}
          </button>
          <div className="mb-3 flex items-center gap-2.5">
            <div className="side-surface flex h-8 w-8 items-center justify-center rounded-lg border bg-accent/10">
              <Server className="h-4 w-4 text-accent" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-slate-200">
                {session?.displayName || session?.username || '—'}
              </p>
              <p className="truncate font-mono text-[10px] text-slate-500">
                @{session?.username || '—'}
              </p>
            </div>
          </div>
          <button
            onClick={logout}
            className="side-surface flex w-full items-center justify-center gap-2 rounded-lg border bg-white/[0.02] py-2 text-sm text-slate-400 transition-colors hover:border-danger/40 hover:text-danger"
          >
            <LogOut className="h-4 w-4" />
            退出登录
          </button>
        </div>
      </aside>

      {/* 主内容 */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}
