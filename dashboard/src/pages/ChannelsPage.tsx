import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Archive,
  ArchiveRestore,
  Eye,
  Hash,
  Loader2,
  Users,
} from 'lucide-react'
import PageHeader from '@/components/PageHeader'
import Badge from '@/components/Badge'
import { usePolling } from '@/hooks/usePolling'
import { api } from '@/api/client'
import { toast } from '@/store/toast'
import type { ChannelListItem } from '@/types'
import { formatNumber, formatTime } from '@/lib/format'
import { cn } from '@/lib/utils'

type ArchiveFilter = 'all' | 'active' | 'archived'

export default function ChannelsPage() {
  const navigate = useNavigate()
  const { data, loading, refresh } = usePolling(
    () => api.listChannels(),
    10000,
  )
  const [filter, setFilter] = useState<ArchiveFilter>('all')
  const [actingId, setActingId] = useState<string | null>(null)

  const channels = data?.channels ?? []
  const filtered = channels.filter((c) => {
    if (filter === 'active') return !c.archived
    if (filter === 'archived') return c.archived
    return true
  })

  const openDetail = (c: ChannelListItem) => {
    navigate(`/channels/${encodeURIComponent(c.channel_id)}`, {
      state: { item: c },
    })
  }

  const toggleArchive = async (c: ChannelListItem) => {
    setActingId(c.channel_id)
    try {
      const res = await api.archiveChannel(c.channel_id, !c.archived)
      toast.success(
        res.archived
          ? `已归档频道 ${c.name || c.channel_id}`
          : `已取消归档 ${c.name || c.channel_id}`,
      )
      refresh()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '操作失败')
    } finally {
      setActingId(null)
    }
  }

  return (
    <div className="mx-auto max-w-[1200px] p-7">
      <PageHeader
        title="频道管理"
        description="查看与管理全部频道 · 点击进入频道详情 · 每 10 秒自动刷新"
      />

      {/* 筛选 */}
      <div className="mb-4 flex items-center gap-2">
        {(
          [
            { key: 'all', label: '全部' },
            { key: 'active', label: '活跃' },
            { key: 'archived', label: '已归档' },
          ] as const
        ).map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={cn(
              'rounded-lg border px-3.5 py-1.5 text-xs font-medium transition-all',
              filter === f.key
                ? 'border-accent/50 bg-accent/10 text-accent-soft'
                : 'border-white/[0.08] text-slate-500 hover:border-white/20 hover:text-slate-300',
            )}
          >
            {f.label}
          </button>
        ))}
        <span className="ml-auto font-mono text-[11px] text-slate-500">
          {filtered.length} / {channels.length}
        </span>
      </div>

      {/* 列表 */}
      <div className="glass-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>频道</th>
                <th>类型</th>
                <th>可见性</th>
                <th className="text-right">信封</th>
                <th className="text-right">成员</th>
                <th>创建时间</th>
                <th>状态</th>
                <th className="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-10 text-center text-sm text-slate-500">
                    加载中…
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-10 text-center text-sm text-slate-500">
                    暂无匹配的频道
                  </td>
                </tr>
              ) : (
                filtered.map((c) => (
                  <tr
                    key={c.channel_id}
                    className="cursor-pointer transition-colors hover:bg-white/[0.02]"
                    onClick={() => openDetail(c)}
                  >
                    <td>
                      <div className="flex items-center gap-3 text-left">
                        <span
                          className={cn(
                            'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border',
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
                        <span className="min-w-0">
                          <span className="block max-w-[180px] truncate text-sm font-medium text-slate-200">
                            {c.name || c.channel_id}
                          </span>
                          <span className="block max-w-[180px] truncate font-mono text-[10px] text-slate-500">
                            {c.channel_id}
                          </span>
                        </span>
                      </div>
                    </td>
                    <td>
                      <Badge
                        tone={
                          c.channel_type === 'dm'
                            ? 'accent'
                            : c.channel_type === 'forum'
                              ? 'warn'
                              : 'muted'
                        }
                      >
                        {c.channel_type}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={c.visibility === 'open' ? 'ok' : 'muted'}>
                        {c.visibility}
                      </Badge>
                    </td>
                    <td className="text-right font-mono">
                      {formatNumber(c.envelope_count)}
                    </td>
                    <td className="text-right font-mono">
                      {formatNumber(c.member_count)}
                    </td>
                    <td className="font-mono text-xs text-slate-500">
                      {formatTime(c.created_at_ms)}
                    </td>
                    <td>
                      <Badge tone={c.archived ? 'muted' : 'ok'}>
                        {c.archived ? '已归档' : '活跃'}
                      </Badge>
                    </td>
                    <td className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            openDetail(c)
                          }}
                          className="btn-ghost !px-2.5 !py-1.5"
                          title="查看详情"
                        >
                          <Eye className="h-3.5 w-3.5" />
                        </button>
                        <button
                          onClick={(e) => {
                            e.stopPropagation()
                            toggleArchive(c)
                          }}
                          disabled={actingId === c.channel_id}
                          className={
                            c.archived
                              ? 'btn-ghost !px-2.5 !py-1.5'
                              : 'btn-danger !px-2.5 !py-1.5'
                          }
                          title={c.archived ? '取消归档' : '归档'}
                        >
                          {actingId === c.channel_id ? (
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                          ) : c.archived ? (
                            <ArchiveRestore className="h-3.5 w-3.5" />
                          ) : (
                            <Archive className="h-3.5 w-3.5" />
                          )}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
