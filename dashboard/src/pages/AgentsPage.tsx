import { Bot, Wifi, WifiOff, Activity } from 'lucide-react'
import PageHeader from '@/components/PageHeader'
import Badge from '@/components/Badge'
import { usePolling } from '@/hooks/usePolling'
import { api } from '@/api/client'
import type { AgentItem } from '@/types'
import { formatNumber, formatRelative, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'

export default function AgentsPage() {
  const { data, loading } = usePolling(() => api.listAgents(), 10000)
  const agents = data?.agents ?? []
  const onlineCount = agents.filter((a) => a.online).length

  return (
    <div className="mx-auto max-w-[1200px] p-7">
      <PageHeader
        title="Agent 管理"
        description="全部已注册 Agent 及在线状态 · 每 10 秒自动刷新"
        meta={
          <Badge tone="ok" className="!text-[11px]">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full rounded-full bg-ok animate-pulseDot" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-ok" />
            </span>
            在线 {onlineCount} / {agents.length}
          </Badge>
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {loading && agents.length === 0
          ? [0, 1, 2, 3, 4, 5].map((i) => (
              <div
                key={i}
                className="h-40 animate-pulse rounded-2xl border border-white/[0.06] bg-white/[0.03]"
              />
            ))
          : agents.length === 0
            ? (
                <div className="glass-card col-span-full py-16 text-center text-sm text-slate-500">
                  暂无已注册 Agent
                </div>
              )
            : agents.map((a, i) => <AgentCard key={a.agentId} a={a} index={i} />)}
      </div>
    </div>
  )
}

function AgentCard({ a, index }: { a: AgentItem; index: number }) {
  return (
    <div
      className="glass-card p-5 animate-rise"
      style={{ animationDelay: `${Math.min(index, 6) * 50}ms` }}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <div
            className={cn(
              'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border',
              a.online
                ? 'border-ok/30 bg-ok/10 text-ok'
                : 'border-slate-500/25 bg-slate-500/10 text-slate-500',
            )}
          >
            <Bot className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <p className="truncate font-mono text-sm font-semibold text-slate-200">
              {a.agentId}
            </p>
            <p className="mt-0.5 flex items-center gap-1.5 text-[11px] text-slate-500">
              {a.online ? (
                <>
                  <Wifi className="h-3 w-3 text-ok" />
                  在线
                </>
              ) : (
                <>
                  <WifiOff className="h-3 w-3" />
                  离线
                </>
              )}
              <span className="text-slate-600">·</span>
              注册于 {formatRelative(a.registeredAtMs)}
            </p>
          </div>
        </div>
        <Badge tone={a.online ? (a.status === 'idle' ? 'ok' : 'warn') : 'muted'}>
          {a.online ? a.status : 'offline'}
        </Badge>
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <div className="rounded-lg border border-white/[0.05] bg-white/[0.015] px-3 py-2.5">
          <p className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-600">
            <Activity className="h-3 w-3" />
            活跃任务
          </p>
          <p className="mt-1 font-mono text-lg font-semibold text-slate-200">
            {formatNumber(a.activeTasks)}
          </p>
        </div>
        <div className="rounded-lg border border-white/[0.05] bg-white/[0.015] px-3 py-2.5">
          <p className="text-[10px] uppercase tracking-wider text-slate-600">
            最近心跳
          </p>
          <p className="mt-1 font-mono text-xs text-slate-300">
            {a.lastHeartbeatMs ? formatTime(a.lastHeartbeatMs) : '—'}
          </p>
        </div>
      </div>
    </div>
  )
}
