import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import {
  ChevronLeft,
  Hash,
  Loader2,
  MessagesSquare,
  RefreshCw,
  Users,
} from 'lucide-react'
import Badge from '@/components/Badge'
import { api } from '@/api/client'
import { toast } from '@/store/toast'
import type {
  ChannelDetail,
  ChannelListItem,
  EnvelopeItem,
} from '@/types'
import { formatNumber, formatTime, formatRelative } from '@/lib/format'
import { cn } from '@/lib/utils'

type DetailTab = 'info' | 'messages'

const KIND_TONE: Record<string, 'accent' | 'warn' | 'ok' | 'danger'> = {
  chat: 'accent',
  task_assign: 'warn',
  task_result: 'ok',
  command: 'danger',
}

export default function ChannelDetailPage() {
  const { channelId } = useParams<{ channelId: string }>()
  const location = useLocation()
  const initial = (location.state as { item?: ChannelListItem } | null)?.item

  const [detail, setDetail] = useState<ChannelDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState<DetailTab>('info')
  const [messages, setMessages] = useState<EnvelopeItem[]>([])
  const [hasMore, setHasMore] = useState(false)
  const [msgLoading, setMsgLoading] = useState(false)
  const [msgLoadingMore, setMsgLoadingMore] = useState(false)
  const [msgLoaded, setMsgLoaded] = useState(false)

  const loadDetail = useCallback(async () => {
    if (!channelId) return
    setLoading(true)
    try {
      const d = await api.channelDetail(channelId)
      setDetail(d)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '加载详情失败')
    } finally {
      setLoading(false)
    }
  }, [channelId])

  useEffect(() => {
    setTab('info')
    setMessages([])
    setHasMore(false)
    setMsgLoaded(false)
    loadDetail()
  }, [channelId, loadDetail])

  /** 加载频道历史信封（倒序，最新在前） */
  const loadMessages = useCallback(
    async (opts?: { beforeMs?: number }) => {
      if (!channelId) return
      const loadingMore = !!opts?.beforeMs
      if (loadingMore) setMsgLoadingMore(true)
      else setMsgLoading(true)
      try {
        const res = await api.channelHistory(channelId, {
          beforeMs: opts?.beforeMs,
          limit: 30,
        })
        setMessages((prev) =>
          opts?.beforeMs ? [...prev, ...res.envelopes] : res.envelopes,
        )
        setHasMore(res.has_more)
        if (!loadingMore) setMsgLoaded(true)
      } catch (e) {
        toast.error(e instanceof Error ? e.message : '加载消息失败')
      } finally {
        if (loadingMore) setMsgLoadingMore(false)
        else setMsgLoading(false)
      }
    },
    [channelId],
  )

  /** 刷新消息并同步最新信封计数 */
  const refreshMessages = async () => {
    await Promise.all([loadMessages(), loadDetail()])
  }

  const switchTab = (t: DetailTab) => {
    setTab(t)
    if (t === 'messages' && !msgLoaded) loadMessages()
  }

  const envelopeCount = detail?.envelope_count ?? initial?.envelope_count ?? 0
  const memberCount = detail?.members.length ?? initial?.member_count ?? 0
  const displayName = detail?.name || initial?.name || channelId || '频道详情'

  /* ── 加载中 ── */
  if (loading && !detail) {
    return (
      <div className="flex items-center justify-center py-40">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    )
  }

  /* ── 加载失败/不存在 ── */
  if (!detail) {
    return (
      <div className="mx-auto max-w-[1200px] p-7">
        <BackLink />
        <div className="glass-card mt-6 py-24 text-center text-sm text-slate-500">
          频道不存在或已被删除
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1200px] p-7">
      <BackLink />

      {/* ── 频道头部 ── */}
      <div className="glass-card mt-4 p-6">
        <div className="flex flex-wrap items-center gap-5">
          <span
            className={cn(
              'flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border',
              detail.archived
                ? 'border-slate-500/25 bg-slate-500/10 text-slate-400'
                : 'border-accent/30 bg-accent/10 text-accent',
            )}
          >
            {detail.channel_type === 'dm' ? (
              <Users className="h-7 w-7" />
            ) : (
              <Hash className="h-7 w-7" />
            )}
          </span>

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2.5">
              <h1 className="font-display text-2xl font-bold tracking-wide text-slate-100">
                {displayName}
              </h1>
              <Badge tone={detail.visibility === 'open' ? 'ok' : 'muted'}>
                {detail.visibility}
              </Badge>
              <Badge
                tone={
                  detail.channel_type === 'dm'
                    ? 'accent'
                    : detail.channel_type === 'forum'
                      ? 'warn'
                      : 'muted'
                }
              >
                {detail.channel_type}
              </Badge>
              <Badge tone={detail.archived ? 'muted' : 'ok'}>
                {detail.archived ? '已归档' : '活跃'}
              </Badge>
            </div>
            <p className="mt-1.5 font-mono text-xs text-slate-500">
              {detail.channel_id}
            </p>
            {detail.description && (
              <p className="mt-2 max-w-2xl text-sm text-slate-400">
                {detail.description}
              </p>
            )}
          </div>

          {/* 统计 */}
          <div className="flex shrink-0 items-center gap-6 pr-2">
            <StatBlock label="信封" value={formatNumber(envelopeCount)} accent />
            <StatBlock label="成员" value={formatNumber(memberCount)} />
            <StatBlock
              label="状态"
              value={detail.archived ? '已归档' : '活跃'}
              muted={detail.archived}
            />
          </div>
        </div>
      </div>

      {/* ── Tab 切换 ── */}
      <div className="mt-6 flex w-fit items-center gap-1 rounded-xl border border-white/[0.06] bg-white/[0.02] p-1">
        {(
          [
            { key: 'info', label: '概览', icon: Users },
            { key: 'messages', label: '消息', icon: MessagesSquare },
          ] as const
        ).map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => switchTab(key)}
            className={cn(
              'flex items-center gap-2 rounded-lg px-5 py-2 text-sm font-medium transition-all',
              tab === key
                ? 'bg-accent/12 text-accent-soft shadow-glow-sm'
                : 'text-slate-400 hover:text-slate-200',
            )}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      {/* ── 内容区 ── */}
      {tab === 'info' ? (
        <InfoSection detail={detail} envelopeCount={envelopeCount} />
      ) : (
        <MessageSection
          detail={detail}
          messages={messages}
          hasMore={hasMore}
          msgLoading={msgLoading}
          msgLoadingMore={msgLoadingMore}
          onRefresh={refreshMessages}
          onLoadMore={() =>
            loadMessages({
              beforeMs: messages[messages.length - 1]?.created_at_ms,
            })
          }
        />
      )}
    </div>
  )
}

function BackLink() {
  return (
    <Link
      to="/channels"
      className="group inline-flex items-center gap-1.5 text-xs font-medium text-slate-500 transition-colors hover:text-accent-soft"
    >
      <ChevronLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" />
      返回频道列表
    </Link>
  )
}

function StatBlock({
  label,
  value,
  accent,
  muted,
}: {
  label: string
  value: string
  accent?: boolean
  muted?: boolean
}) {
  return (
    <div className="text-right">
      <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">
        {label}
      </p>
      <p
        className={cn(
          'mt-0.5 font-display text-lg font-bold',
          muted
            ? 'text-slate-500'
            : accent
              ? 'text-accent-soft'
              : 'text-slate-200',
        )}
      >
        {value}
      </p>
    </div>
  )
}

/* ── 概览区块 ── */
function InfoSection({
  detail,
  envelopeCount,
}: {
  detail: ChannelDetail
  envelopeCount: number
}) {
  return (
    <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-3">
      {/* 左：成员列表 */}
      <div className="glass-card lg:col-span-2">
        <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-4">
          <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
            <Users className="h-4 w-4 text-accent" />
            成员
            <span className="font-mono text-xs font-normal text-slate-500">
              {detail.members.length}
            </span>
          </h3>
        </div>
        {detail.members.length === 0 ? (
          <p className="py-16 text-center text-sm text-slate-600">暂无成员</p>
        ) : (
          <ul className="divide-y divide-white/[0.04]">
            {detail.members.map((m) => (
              <li
                key={m.pubkey}
                className="flex items-center justify-between px-5 py-3.5 transition-colors hover:bg-white/[0.02]"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={cn(
                      'flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-bold',
                      m.role === 'admin'
                        ? 'bg-warn/15 text-warn'
                        : 'bg-accent/15 text-accent',
                    )}
                  >
                    {(m.name || m.pubkey).slice(0, 1).toUpperCase()}
                  </span>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-200">
                      {m.name || '—'}
                    </p>
                    <p className="truncate font-mono text-[11px] text-slate-500">
                      {m.pubkey}
                    </p>
                  </div>
                </div>
                <Badge tone={m.role === 'admin' ? 'warn' : 'muted'}>
                  {m.role}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* 右：频道信息 */}
      <div className="space-y-5">
        <div className="glass-card p-5">
          <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
            <Hash className="h-4 w-4 text-accent" />
            频道信息
          </h3>
          <dl className="mt-4 space-y-3.5">
            <InfoRow label="频道 ID" mono>
              {detail.channel_id}
            </InfoRow>
            <InfoRow label="类型">{detail.channel_type}</InfoRow>
            <InfoRow label="可见性">{detail.visibility}</InfoRow>
            <InfoRow label="状态">
              {detail.archived ? '已归档' : '活跃'}
            </InfoRow>
            <InfoRow label="信封数">{formatNumber(envelopeCount)}</InfoRow>
          </dl>
        </div>

        {detail.description && (
          <div className="glass-card p-5">
            <h3 className="text-sm font-semibold text-slate-200">描述</h3>
            <p className="mt-2 text-sm leading-relaxed text-slate-400">
              {detail.description}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

function InfoRow({
  label,
  children,
  mono,
}: {
  label: string
  children: ReactNode
  mono?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="shrink-0 text-xs text-slate-500">{label}</dt>
      <dd
        className={cn(
          'truncate text-sm text-slate-200',
          mono && 'font-mono text-xs',
        )}
      >
        {children}
      </dd>
    </div>
  )
}

/* ── 消息列表区块 ── */
function MessageSection({
  detail,
  messages,
  hasMore,
  msgLoading,
  msgLoadingMore,
  onRefresh,
  onLoadMore,
}: {
  detail: ChannelDetail
  messages: EnvelopeItem[]
  hasMore: boolean
  msgLoading: boolean
  msgLoadingMore: boolean
  onRefresh: () => void
  onLoadMore: () => void
}) {
  return (
    <div className="mt-6">
      {/* 工具栏 */}
      <div className="mb-3 flex items-center justify-between">
        <p className="text-sm text-slate-500">
          <span className="font-mono text-accent-soft">
            {formatNumber(detail.envelope_count)}
          </span>{' '}
          条信封 · 最新在前
        </p>
        <button
          onClick={onRefresh}
          disabled={msgLoading}
          className="btn-ghost !px-3 !py-2 !text-xs"
          title="刷新消息"
        >
          <RefreshCw
            className={cn('h-4 w-4', msgLoading && 'animate-spin')}
          />
          刷新
        </button>
      </div>

      {/* 列表 */}
      {msgLoading && messages.length === 0 ? (
        <div className="glass-card flex items-center justify-center py-24">
          <Loader2 className="h-7 w-7 animate-spin text-accent" />
        </div>
      ) : messages.length === 0 ? (
        <div className="glass-card flex flex-col items-center gap-2 py-24 text-center">
          <MessagesSquare className="h-8 w-8 text-slate-600" />
          <p className="text-sm text-slate-600">该频道暂无消息</p>
        </div>
      ) : (
        <div className="space-y-3">
          {messages.map((m) => (
            <EnvelopeCard key={m.envelope_id} env={m} />
          ))}
        </div>
      )}

      {/* 加载更早 */}
      {messages.length > 0 && (
        <div className="pt-4 text-center">
          {hasMore ? (
            <button
              onClick={onLoadMore}
              disabled={msgLoadingMore}
              className="btn-ghost !px-6 !py-2.5 !text-xs"
            >
              {msgLoadingMore && (
                <Loader2 className="h-4 w-4 animate-spin" />
              )}
              加载更早的消息
            </button>
          ) : (
            <p className="flex items-center justify-center gap-1.5 text-[11px] text-slate-600">
              <ChevronLeft className="h-3.5 w-3.5" />
              已到达最早消息
            </p>
          )}
        </div>
      )}
    </div>
  )
}

/* ── 单条信封卡片 ── */
function EnvelopeCard({ env }: { env: EnvelopeItem }) {
  const payload = env.chat ?? env.task_assign ?? env.task_result ?? env.command
  const content =
    typeof payload?.content === 'string'
      ? payload.content
      : payload
        ? JSON.stringify(payload, null, 2)
        : null
  const kindTone = KIND_TONE[env.kind as string]

  return (
    <div className="rounded-xl border border-white/[0.06] bg-white/[0.015] p-4 transition-colors hover:border-accent/25">
      {/* 头行：发送者 + kind + 时间 */}
      <div className="flex items-center gap-2.5">
        <span
          className={cn(
            'flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-[11px] font-bold',
            env.sender_role === 'admin'
              ? 'bg-warn/15 text-warn'
              : 'bg-accent/12 text-accent',
          )}
        >
          {(env.sender_id || '?').slice(0, 1).toUpperCase()}
        </span>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="truncate font-mono text-xs font-medium text-slate-200">
              {env.sender_id}
            </span>
            <Badge tone="muted" className="!px-1.5 !py-0 !text-[10px]">
              {env.sender_role}
            </Badge>
            {kindTone && (
              <Badge tone={kindTone} className="!px-1.5 !py-0 !text-[10px]">
                {env.kind}
              </Badge>
            )}
          </div>
          <p className="mt-0.5 font-mono text-[10px] text-slate-600">
            {env.envelope_id}
          </p>
        </div>
        <span className="ml-auto shrink-0 font-mono text-[11px] text-slate-500">
          {formatRelative(env.created_at_ms)}
        </span>
      </div>

      {/* 内容 */}
      {content && (
        <pre className="mt-3 whitespace-pre-wrap break-words rounded-lg border border-white/[0.04] bg-black/20 p-3 font-mono text-xs leading-relaxed text-slate-400">
          {content.length > 600 ? `${content.slice(0, 600)}…` : content}
        </pre>
      )}

      {/* 底部元信息 */}
      <div className="mt-2.5 flex items-center gap-3 text-[10px] text-slate-600">
        {env.trace_id && (
          <span className="truncate font-mono">trace: {env.trace_id}</span>
        )}
        {env.mentions?.length > 0 && (
          <span className="shrink-0 text-accent/70">
            @ {env.mentions.join(', ')}
          </span>
        )}
        <span className="ml-auto shrink-0 font-mono">
          {formatTime(env.created_at_ms)}
        </span>
      </div>
    </div>
  )
}
