/** 后端 REST API 类型定义（与 ACP Relay Java 后端对应） */

export interface LoginResult {
  ok: boolean
  username: string
  display_name: string
  enabled: boolean
  last_login_at_ms: number | null
}

export interface ApiError {
  ok: false
  error: string
  message?: string
}

export interface Stats {
  instance_id: string
  envelope_count: number
  channel_count: number
  agent_count: number
  super_admin_count: number
  online_agent_count: number
}

export type ChannelType = 'stream' | 'forum' | 'dm'
export type Visibility = 'open' | 'private'

export interface ChannelListItem {
  channel_id: string
  name: string
  channel_type: ChannelType
  visibility: Visibility
  description: string
  archived: boolean
  created_at_ms: number
  member_count: number
  envelope_count: number
}

export interface ChannelMember {
  pubkey: string
  name: string
  role: 'admin' | 'member'
}

export interface ChannelInfo {
  type: 'channel_info'
  channel_id: string
  name: string
  channel_type: ChannelType
  visibility: Visibility
  description: string
  members: ChannelMember[]
  archived: boolean
  is_dm: boolean
}

export interface ChannelDetail {
  envelope_count: number
  members: ChannelMember[]
  name: string
  channel_id: string
  channel_type: ChannelType
  visibility: Visibility
  description: string
  archived: boolean
  is_dm: boolean
}

export interface AgentItem {
  agentId: string
  status: string
  activeTasks: number
  registeredAtMs: number
  lastHeartbeatMs: number | null
  online: boolean
}

/** 信封消息（/api/history 返回） */
export interface EnvelopeItem {
  envelope_id: string
  channel_id: string
  project_id?: string
  topic_id?: string
  parent_envelope_id?: string
  root_envelope_id?: string
  sender_id: string
  sender_role: string
  kind: 'chat' | 'task_assign' | 'task_result' | 'command' | string
  mentions: string[]
  created_at_ms: number
  trace_id?: string
  metadata?: Record<string, string>
  /** 按 kind 还原的 payload，四选一 */
  chat?: { content?: string; [k: string]: unknown }
  task_assign?: { content?: string; task_id?: string; [k: string]: unknown }
  task_result?: { content?: string; task_id?: string; status?: string; [k: string]: unknown }
  command?: { content?: string; [k: string]: unknown }
}

export interface HistoryResult {
  envelopes: EnvelopeItem[]
  has_more: boolean
}

export interface OnlineAgent {
  agent_id: string
  instance_id: string
}

export interface AdminAccount {
  username: string
  display_name: string
  enabled: boolean
  created_at_ms: number
  last_login_at_ms: number | null
}
