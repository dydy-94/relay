import type { ReactNode } from 'react'
import { X } from 'lucide-react'

export default function Modal({
  open,
  title,
  onClose,
  children,
  width = 'max-w-md',
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  width?: string
}) {
  if (!open) return null
  return (
    <div
      className="fixed inset-0 z-[90] flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      />
      <div
        className={`relative w-full ${width} rounded-2xl border border-slate-600/25 bg-space-850 p-6 shadow-card animate-rise`}
      >
        <div className="mb-5 flex items-center justify-between">
          <h3 className="font-display text-base font-semibold text-slate-100">
            {title}
          </h3>
          <button
            onClick={onClose}
            className="text-slate-500 transition-colors hover:text-slate-200"
            aria-label="关闭"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
