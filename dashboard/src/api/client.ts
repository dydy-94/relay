import type {
  LoginResult,
  Stats,
  ChannelListItem,
  ChannelDetail,
  AgentItem,
  AdminAccount,
  HistoryResult,
} from '@/types'

/**
 * API 客户端 — 统一封装 fetch.
 *
 * 登录态：账号 id 保存在 localStorage，请求自动附加 `X-Agent-Id` header.
 * 403 响应：清空登录态并跳转登录页.
 */

const STORAGE_KEY = 'relay_dash_username'
const SESSION_KEY = 'relay_dash_session'

export interface Session {
  username: string
  displayName: string
}

export function getStoredUsername(): string {
  return localStorage.getItem(STORAGE_KEY) ?? ''
}

export function getSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Session
  } catch {
    return null
  }
}

export function saveSession(s: Session) {
  localStorage.setItem(STORAGE_KEY, s.username)
  localStorage.setItem(SESSION_KEY, JSON.stringify(s))
}

export function clearSession() {
  localStorage.removeItem(STORAGE_KEY)
  localStorage.removeItem(SESSION_KEY)
}

export function redirectToLogin() {
  clearSession()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

interface ApiErrorLike {
  message?: string
  error?: string
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  }
  const username = getStoredUsername()
  if (username) headers['X-Agent-Id'] = username

  const res = await fetch(path, { ...init, headers })

  if (res.status === 403) {
    redirectToLogin()
    throw new Error('登录已失效，请重新登录')
  }

  let data: unknown = null
  const text = await res.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!res.ok) {
    const err = (data as ApiErrorLike) ?? {}
    throw new Error(err.message ?? `请求失败 (${res.status})`)
  }
  return data as T
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResult>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  stats: () => request<Stats>('/api/admin/stats'),

  listChannels: () =>
    request<{ channels: ChannelListItem[] }>('/api/channels'),

  channelDetail: (channelId: string) =>
    request<ChannelDetail>(`/api/channels/${encodeURIComponent(channelId)}`),

  archiveChannel: (channelId: string, archived: boolean) =>
    request<{ ok: boolean; channel_id: string; archived: boolean }>(
      `/api/channels/${encodeURIComponent(channelId)}/archive`,
      { method: 'POST', body: JSON.stringify({ archived }) },
    ),

  /** 查询频道历史信封（倒序，beforeMs 为加载更早的游标） */
  channelHistory: (channelId: string, opts?: { beforeMs?: number; limit?: number }) => {
    const qs = new URLSearchParams({ channel_id: channelId })
    if (opts?.beforeMs != null) qs.set('before_ms', String(opts.beforeMs))
    if (opts?.limit != null) qs.set('limit', String(opts.limit))
    return request<HistoryResult>(`/api/history?${qs.toString()}`)
  },

  listAgents: () => request<{ agents: AgentItem[] }>('/api/agents'),

  listAccounts: () =>
    request<{ accounts: AdminAccount[] }>('/api/admin/accounts'),

  createAccount: (username: string, password: string, displayName: string) =>
    request<{ ok: boolean; username: string }>('/api/admin/accounts', {
      method: 'POST',
      body: JSON.stringify({ username, password, display_name: displayName }),
    }),

  deleteAccount: (username: string) =>
    request<{ ok: boolean }>(
      `/api/admin/accounts/${encodeURIComponent(username)}`,
      { method: 'DELETE' },
    ),

  resetPassword: (username: string, password: string) =>
    request<{ ok: boolean }>(
      `/api/admin/accounts/${encodeURIComponent(username)}/password`,
      { method: 'POST', body: JSON.stringify({ password }) },
    ),
}
