import type { ReactNode } from 'react'

/** 页面顶部：标题 + 描述 + 右侧操作区 */
export default function PageHeader({
  title,
  description,
  actions,
  meta,
}: {
  title: string
  description?: string
  actions?: ReactNode
  meta?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <div className="flex items-center gap-3">
          <h1 className="font-display text-xl font-bold tracking-wide text-slate-100">
            {title}
          </h1>
          {meta}
        </div>
        {description && (
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        )}
      </div>
      <div className="flex items-center gap-2.5">{actions}</div>
    </div>
  )
}
