import { Link } from 'react-router-dom'
import {
  Mail,
  MessagesSquare,
  Bot,
  ShieldCheck,
  Wifi,
  ArrowRight,
  Hash,
  Users,
} from 'lucide-react'
import StatCard from '@/components/StatCard'
import Badge from '@/components/Badge'
import PageHeader from '@/components/PageHeader'
import { usePolling } from '@/hooks/usePolling'
import { api } from '@/api/client'
import type { ChannelListItem } from '@/types'
import { formatNumber, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'

export default function OverviewPage() {
  const { data: stats, loading } = usePolling(() => api.stats(), 15000)
  const { data: channels } = usePolling(() => api.listChannels(), 15000)
  const { data: agents } = usePolling(() => api.listAgents(), 15000)

  const channelList = channels?.channels ?? []
  const recentChannels = [...channelList]
    .sort((a, b) => b.created_at_ms - a.created_at_ms)
    .slice(0, 6)
  const onlineAgents = (agents?.agents ?? []).filter((a) => a.online)
  const idleAgents = onlineAgents.filter((a) => a.status === 'idle').length

  return (
    <div className="mx-auto max-w-[1200px] p-7">
      <PageHeader
        title="任务总览"
        description="ACP Relay 全局运行状态 · 每 15 秒自动刷新"
      />

      {/* 统计卡片 */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
        <StatCard
          index={0}
          label="消息信封"
          value={stats?.envelope_count ?? 0}
          icon={Mail}
          hint="已转发信封总量"
          accent="accent"
        />
        <StatCard
          index={1}
          label="频道"
          value={stats?.channel_count ?? 0}
          icon={MessagesSquare}
          hint="含归档频道"
          accent="ok"
        />
        <StatCard
          index={2}
          label="Agent"
          value={stats?.agent_count ?? 0}
          icon={Bot}
          hint="已注册 Agent 总数"
          accent="warn"
        />
        <StatCard
          index={3}
          label="超管账号"
          value={stats?.super_admin_count ?? 0}
          icon={ShieldCheck}
          hint="可登录控制台"
          accent="danger"
        />
        <StatCard
          index={4}
          label="在线 Agent"
          value={stats?.online_agent_count ?? 0}
          icon={Wifi}
          hint={stats ? `在线中 ${idleAgents} 个空闲` : undefined}
          accent="ok"
        />
      </div>

      {/* 第二行：频道概览 + 在线 Agent */}
      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-5">
        {/* 频道概览 */}
        <div className="glass-card xl:col-span-3">
          <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-4">
            <div className="flex items-center gap-2">
              <MessagesSquare className="h-4 w-4 text-accent" />
              <h2 className="text-sm font-semibold text-slate-200">频道概览</h2>
            </div>
            <Link
              to="/channels"
              className="flex items-center gap-1 text-xs text-accent transition-colors hover:text-accent-soft"
            >
              全部频道 <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          {loading && channelList.length === 0 ? (
            <div className="space-y-3 p-5">
              {[0, 1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="h-12 animate-pulse rounded-lg bg-white/[0.04]"
                />
              ))}
            </div>
          ) : channelList.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              暂无频道
            </div>
          ) : (
            <div className="divide-y divide-white/[0.05]">
              {recentChannels.map((c) => (
                <ChannelRow key={c.channel_id} c={c} />
              ))}
            </div>
          )}
        </div>

        {/* 在线 Agent */}
        <div className="glass-card xl:col-span-2">
          <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-4">
            <div className="flex items-center gap-2">
              <Wifi className="h-4 w-4 text-ok" />
              <h2 className="text-sm font-semibold text-slate-200">在线 Agent</h2>
            </div>
            <Link
              to="/agents"
              className="flex items-center gap-1 text-xs text-accent transition-colors hover:text-accent-soft"
            >
              全部 Agent <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          {onlineAgents.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-500">
              {loading ? '加载中…' : '当前无在线 Agent'}
            </div>
          ) : (
            <div className="max-h-[380px] divide-y divide-white/[0.05] overflow-y-auto">
              {onlineAgents.map((a) => (
                <div
                  key={a.agentId}
                  className="flex items-center justify-between px-5 py-3"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="relative flex h-2.5 w-2.5 shrink-0">
                      <span className="absolute inline-flex h-full w-full rounded-full bg-ok animate-pulseDot" />
                      <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-ok" />
                    </span>
                    <span className="truncate font-mono text-sm text-slate-200">
                      {a.agentId}
                    </span>
                  </div>
                  <Badge tone={a.status === 'idle' ? 'ok' : 'warn'}>
                    {a.status}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 实例信息条 */}
      {stats && (
        <div className="mt-6 flex items-center justify-between rounded-xl border border-white/[0.06] bg-space-950/50 px-5 py-3">
          <span className="flex items-center gap-2 text-xs text-slate-500">
            <span className="inline-block h-1.5 w-1.5 rounded-full bg-ok" />
            当前实例
            <code className="rounded bg-white/5 px-1.5 py-0.5 font-mono text-accent/80">
              {stats.instance_id}
            </code>
          </span>
          <span className="font-mono text-[11px] text-slate-600">
            UPTIME-LINK ESTABLISHED
          </span>
        </div>
      )}
    </div>
  )
}

function ChannelRow({ c }: { c: ChannelListItem }) {
  return (
    <Link
      to={`/channels`}
      className="flex items-center gap-4 px-5 py-3.5 transition-colors hover:bg-accent/[0.03]"
    >
      <span
        className={cn(
          'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border',
          c.archived
            ? 'border-slate-500/25 bg-slate-500/10 text-slate-400'
            : 'border-accent/25 bg-accent/10 text-accent',
        )}
      >
        {c.channel_type === 'dm' ? (
          <Users className="h-4 w-4" />
        ) : (
          <Hash className="h-4 w-4" />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-medium text-slate-200">
            {c.name || c.channel_id}
          </p>
          {c.archived && <Badge tone="muted">已归档</Badge>}
        </div>
        <p className="truncate font-mono text-[11px] text-slate-500">
          {c.channel_id}
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-4 text-right">
        <div>
          <p className="font-mono text-sm text-slate-200">
            {formatNumber(c.envelope_count)}
          </p>
          <p className="text-[10px] uppercase tracking-wider text-slate-600">
            信封
          </p>
        </div>
        <div>
          <p className="font-mono text-sm text-slate-200">
            {formatNumber(c.member_count)}
          </p>
          <p className="text-[10px] uppercase tracking-wider text-slate-600">
            成员
          </p>
        </div>
      </div>
      <span className="hidden w-24 text-[11px] text-slate-600 sm:block">
        {formatRelative(c.created_at_ms)}
      </span>
    </Link>
  )
}
