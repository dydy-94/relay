import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type Tone = 'accent' | 'ok' | 'warn' | 'danger' | 'muted'

const TONES: Record<Tone, string> = {
  accent: 'border-accent/30 bg-accent/10 text-accent-soft',
  ok: 'border-ok/30 bg-ok/10 text-ok',
  warn: 'border-warn/30 bg-warn/10 text-warn',
  danger: 'border-danger/30 bg-danger/10 text-danger',
  muted: 'border-slate-500/25 bg-slate-500/10 text-slate-400',
}

export default function Badge({
  tone = 'muted',
  children,
  className,
}: {
  tone?: Tone
  children: ReactNode
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[11px] font-medium',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}
