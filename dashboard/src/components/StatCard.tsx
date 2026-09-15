import { useEffect, useRef, useState } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

/** 数字滚动动画：从 0 滚动到目标值 */
function useAnimatedNumber(target: number, duration = 900) {
  const [value, setValue] = useState(0)
  const prev = useRef(0)

  useEffect(() => {
    const from = prev.current
    const start = performance.now()
    let raf = 0
    const step = (now: number) => {
      const p = Math.min((now - start) / duration, 1)
      const eased = 1 - Math.pow(1 - p, 3)
      setValue(Math.round(from + (target - from) * eased))
      if (p < 1) raf = requestAnimationFrame(step)
      else prev.current = target
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [target, duration])

  return value
}

export default function StatCard({
  label,
  value,
  icon: Icon,
  hint,
  index = 0,
  accent = 'accent',
  format = (n: number) => n.toLocaleString('en-US'),
}: {
  label: string
  value: number
  icon: LucideIcon
  hint?: string
  index?: number
  accent?: 'accent' | 'ok' | 'warn' | 'danger'
  format?: (n: number) => string
}) {
  const animated = useAnimatedNumber(value)
  const iconTone = {
    accent: 'text-accent',
    ok: 'text-ok',
    warn: 'text-warn',
    danger: 'text-danger',
  }[accent]

  return (
    <div
      className="glass-card group relative overflow-hidden p-5 animate-rise"
      style={{ animationDelay: `${index * 70}ms` }}
    >
      {/* 顶部微光 */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/40 to-transparent opacity-60" />
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.14em] text-slate-500">
            {label}
          </p>
          <p className="stat-value mt-3 font-mono text-[2rem] font-semibold leading-none">
            {format(animated)}
          </p>
          {hint && (
            <p className="mt-2 text-[11px] text-slate-500">{hint}</p>
          )}
        </div>
        <div
          className={cn(
            'flex h-10 w-10 items-center justify-center rounded-xl border transition-colors',
            'border-white/10 bg-white/[0.03] group-hover:border-accent/40',
          )}
        >
          <Icon className={cn('h-5 w-5', iconTone)} />
        </div>
      </div>
    </div>
  )
}
