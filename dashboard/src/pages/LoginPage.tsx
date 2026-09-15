import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Radio, Loader2, ShieldCheck, ArrowRight, Sun, Moon } from 'lucide-react'
import { api, saveSession } from '@/api/client'
import { toast } from '@/store/toast'
import { useTheme } from '@/hooks/useTheme'

export default function LoginPage() {
  const navigate = useNavigate()
  const { theme, toggle } = useTheme()
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (loading) return
    if (!username.trim() || !password) {
      setError('请输入账号和密码')
      return
    }
    setLoading(true)
    setError('')
    try {
      const res = await api.login(username.trim(), password)
      saveSession({ username: res.username, displayName: res.display_name || res.username })
      toast.success(`欢迎回来，${res.display_name || res.username}`)
      navigate('/overview', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mission-bg relative flex min-h-screen items-center justify-center overflow-hidden p-4">
      <div className="top-beam" />

      {/* 主题切换（右上角悬浮） */}
      <button
        onClick={toggle}
        title={theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'}
        className="absolute right-5 top-5 flex h-10 w-10 items-center justify-center rounded-xl border border-white/[0.08] bg-white/[0.03] backdrop-blur transition-colors hover:border-accent/40 hover:text-accent"
      >
        {theme === 'dark' ? (
          <Sun className="h-5 w-5 text-accent" />
        ) : (
          <Moon className="h-5 w-5 text-accent" />
        )}
      </button>

      {/* 背景装饰：光环 */}
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-[560px] w-[860px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-accent/[0.07]" />
      <div className="pointer-events-none absolute left-1/2 top-1/2 h-[380px] w-[600px] -translate-x-1/2 -translate-y-1/2 rounded-full border border-accent/[0.05]" />

      <div className="relative w-full max-w-[400px] animate-rise">
        {/* Logo 区 */}
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="relative mb-4 flex h-14 w-14 items-center justify-center rounded-2xl border border-accent/40 bg-accent/10 shadow-glow">
            <Radio className="h-7 w-7 text-accent" />
            <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-ok animate-breathe" />
          </div>
          <h1 className="font-display text-2xl font-bold tracking-wide text-slate-100">
            ACP Relay
          </h1>
          <p className="mt-1 text-[11px] uppercase tracking-[0.3em] text-slate-500">
            Mission Control · 管理控制台
          </p>
        </div>

        {/* 登录卡片 */}
        <div className="glass-card p-7 shadow-card">
          <form onSubmit={submit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
                账号
              </label>
              <input
                className="field-input font-mono"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                autoComplete="username"
                autoFocus
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
                密码
              </label>
              <input
                className="field-input font-mono"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
              />
            </div>

            {error && (
              <p className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
                {error}
              </p>
            )}

            <button type="submit" disabled={loading} className="btn-primary w-full !py-2.5">
              {loading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  正在验证…
                </>
              ) : (
                <>
                  进入控制台
                  <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </form>

          <div className="mt-5 flex items-start gap-2 rounded-lg border border-white/[0.06] bg-white/[0.02] px-3 py-2.5">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-accent/70" />
            <p className="text-[11px] leading-relaxed text-slate-500">
              账号由运维在数据库或账号管理页维护。首次启动默认账号
              <code className="mx-1 rounded bg-white/10 px-1 font-mono text-slate-300">admin / admin123</code>
            </p>
          </div>
        </div>

        <p className="mt-6 text-center text-[11px] text-slate-600">
          ACP Relay v1.0 · Java / Spring Boot / MySQL / Redis
        </p>
      </div>
    </div>
  )
}
