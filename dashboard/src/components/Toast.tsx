import { CheckCircle2, XCircle, Info, X } from 'lucide-react'
import { useToastStore, type ToastItem, type ToastKind } from '@/store/toast'
import { cn } from '@/lib/utils'

const ICONS: Record<ToastKind, typeof CheckCircle2> = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
}

const COLORS: Record<ToastKind, string> = {
  success: 'text-ok',
  error: 'text-danger',
  info: 'text-accent',
}

function ToastCard({ t }: { t: ToastItem }) {
  const dismiss = useToastStore((s) => s.dismiss)
  const Icon = ICONS[t.kind]
  return (
    <div
      className={cn(
        'pointer-events-auto flex w-80 items-start gap-3 rounded-xl border bg-space-800/95 px-4 py-3 shadow-card backdrop-blur-md animate-rise',
        t.kind === 'success' && 'border-ok/30',
        t.kind === 'error' && 'border-danger/30',
        t.kind === 'info' && 'border-accent/30',
      )}
      role="status"
    >
      <Icon className={cn('mt-0.5 h-5 w-5 shrink-0', COLORS[t.kind])} />
      <p className="flex-1 text-sm leading-snug text-slate-200">{t.message}</p>
      <button
        onClick={() => dismiss(t.id)}
        className="text-slate-500 transition-colors hover:text-slate-300"
        aria-label="关闭"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  )
}

export default function ToastHost() {
  const toasts = useToastStore((s) => s.toasts)
  return (
    <div className="pointer-events-none fixed right-5 top-5 z-[100] flex flex-col gap-3">
      {toasts.map((t) => (
        <ToastCard key={t.id} t={t} />
      ))}
    </div>
  )
}
