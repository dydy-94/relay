import { useState } from 'react'
import { KeyRound, Loader2, Plus, ShieldCheck, Trash2 } from 'lucide-react'
import PageHeader from '@/components/PageHeader'
import Badge from '@/components/Badge'
import Modal from '@/components/Modal'
import { usePolling } from '@/hooks/usePolling'
import { api, getStoredUsername } from '@/api/client'
import { toast } from '@/store/toast'
import type { AdminAccount } from '@/types'
import { formatTime } from '@/lib/format'

export default function AccountsPage() {
  const { data, loading, refresh } = usePolling(() => api.listAccounts(), 10000)
  const accounts = data?.accounts ?? []
  const me = getStoredUsername()

  const [createOpen, setCreateOpen] = useState(false)
  const [resetTarget, setResetTarget] = useState<AdminAccount | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AdminAccount | null>(null)
  const [busy, setBusy] = useState(false)

  const createAccount = async (username: string, password: string, displayName: string) => {
    setBusy(true)
    try {
      await api.createAccount(username, password, displayName)
      toast.success(`已创建账号 ${username}`)
      setCreateOpen(false)
      refresh()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '创建失败')
    } finally {
      setBusy(false)
    }
  }

  const resetPassword = async (username: string, password: string) => {
    setBusy(true)
    try {
      await api.resetPassword(username, password)
      toast.success(`已重置 ${username} 的密码`)
      setResetTarget(null)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '重置失败')
    } finally {
      setBusy(false)
    }
  }

  const deleteAccount = async (username: string) => {
    setBusy(true)
    try {
      await api.deleteAccount(username)
      toast.success(`已删除账号 ${username}`)
      setDeleteTarget(null)
      refresh()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : '删除失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-[1100px] p-7">
      <PageHeader
        title="账号管理"
        description="维护可登录控制台的超管账号"
        actions={
          <button className="btn-primary" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            新建账号
          </button>
        }
      />

      <div className="glass-card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th>账号</th>
                <th>显示名</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>最近登录</th>
                <th className="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading && accounts.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-10 text-center text-sm text-slate-500">
                    加载中…
                  </td>
                </tr>
              ) : (
                accounts.map((a) => (
                  <tr key={a.username}>
                    <td>
                      <div className="flex items-center gap-2.5">
                        <span className="flex h-8 w-8 items-center justify-center rounded-lg border border-accent/25 bg-accent/10">
                          <ShieldCheck className="h-4 w-4 text-accent" />
                        </span>
                        <span className="font-mono text-sm text-slate-200">
                          {a.username}
                        </span>
                        {a.username === me && (
                          <Badge tone="accent" className="!text-[10px]">
                            当前账号
                          </Badge>
                        )}
                      </div>
                    </td>
                    <td className="text-sm text-slate-400">
                      {a.display_name || '—'}
                    </td>
                    <td>
                      <Badge tone={a.enabled ? 'ok' : 'muted'}>
                        {a.enabled ? '启用' : '禁用'}
                      </Badge>
                    </td>
                    <td className="font-mono text-xs text-slate-500">
                      {formatTime(a.created_at_ms)}
                    </td>
                    <td className="font-mono text-xs text-slate-500">
                      {formatTime(a.last_login_at_ms)}
                    </td>
                    <td className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => setResetTarget(a)}
                          className="btn-ghost !px-2.5 !py-1.5"
                          title="重置密码"
                        >
                          <KeyRound className="h-3.5 w-3.5" />
                        </button>
                        <button
                          onClick={() => setDeleteTarget(a)}
                          disabled={a.username === me}
                          className="btn-danger !px-2.5 !py-1.5 disabled:pointer-events-none disabled:opacity-30"
                          title={
                            a.username === me ? '不能删除当前账号' : '删除账号'
                          }
                        >
                          <Trash2 className="h-3.5 w-3.5" />
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

      <p className="mt-3 text-xs text-slate-600">
        提示：删除或重置操作需要当前账号处于登录状态，请求会自动携带{' '}
        <code className="rounded bg-white/5 px-1 font-mono text-accent/80">
          X-Agent-Id
        </code>{' '}
        header。
      </p>

      <CreateAccountModal
        open={createOpen}
        busy={busy}
        onClose={() => setCreateOpen(false)}
        onSubmit={createAccount}
      />
      <ResetPasswordModal
        target={resetTarget}
        busy={busy}
        onClose={() => setResetTarget(null)}
        onSubmit={resetPassword}
      />
      <DeleteAccountModal
        target={deleteTarget}
        busy={busy}
        onClose={() => setDeleteTarget(null)}
        onConfirm={deleteAccount}
      />
    </div>
  )
}

function CreateAccountModal({
  open,
  busy,
  onClose,
  onSubmit,
}: {
  open: boolean
  busy: boolean
  onClose: () => void
  onSubmit: (u: string, p: string, d: string) => void
}) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || password.length < 6) return
    onSubmit(username.trim(), password, displayName.trim())
  }

  return (
    <Modal
      open={open}
      title="新建超管账号"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
            账号
          </label>
          <input
            className="field-input font-mono"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="登录账号（唯一）"
            autoFocus
          />
        </div>
        <div>
          <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
            密码
          </label>
          <input
            className="field-input font-mono"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="至少 6 位"
          />
        </div>
        <div>
          <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
            显示名（可选）
          </label>
          <input
            className="field-input"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="例如：运维小王"
          />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            className="btn-ghost"
            onClick={onClose}
            disabled={busy}
          >
            取消
          </button>
          <button
            type="submit"
            className="btn-primary"
            disabled={busy || !username.trim() || password.length < 6}
          >
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            创建
          </button>
        </div>
      </form>
    </Modal>
  )
}

function ResetPasswordModal({
  target,
  busy,
  onClose,
  onSubmit,
}: {
  target: AdminAccount | null
  busy: boolean
  onClose: () => void
  onSubmit: (u: string, p: string) => void
}) {
  const [password, setPassword] = useState('')

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!target || password.length < 6) return
    onSubmit(target.username, password)
  }

  return (
    <Modal
      open={!!target}
      title={target ? `重置密码 · ${target.username}` : '重置密码'}
      onClose={() => {
        if (!busy) {
          onClose()
          setPassword('')
        }
      }}
    >
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="mb-1.5 block text-xs font-medium uppercase tracking-wider text-slate-500">
            新密码
          </label>
          <input
            className="field-input font-mono"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="至少 6 位"
            autoFocus
          />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <button
            type="button"
            className="btn-ghost"
            onClick={() => {
              onClose()
              setPassword('')
            }}
            disabled={busy}
          >
            取消
          </button>
          <button
            type="submit"
            className="btn-primary"
            disabled={busy || password.length < 6}
          >
            {busy && <Loader2 className="h-4 w-4 animate-spin" />}
            确认重置
          </button>
        </div>
      </form>
    </Modal>
  )
}

function DeleteAccountModal({
  target,
  busy,
  onClose,
  onConfirm,
}: {
  target: AdminAccount | null
  busy: boolean
  onClose: () => void
  onConfirm: (u: string) => void
}) {
  return (
    <Modal
      open={!!target}
      title="删除账号"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <p className="text-sm leading-relaxed text-slate-400">
        确定要删除账号
        <code className="mx-1 rounded bg-white/5 px-1.5 py-0.5 font-mono text-danger">
          {target?.username}
        </code>
        吗？该账号将立即无法登录控制台，此操作不可撤销。
      </p>
      <div className="mt-6 flex justify-end gap-2">
        <button
          className="btn-ghost"
          onClick={onClose}
          disabled={busy}
        >
          取消
        </button>
        <button
          className="btn-danger !px-4 !py-2 !text-sm"
          onClick={() => target && onConfirm(target.username)}
          disabled={busy}
        >
          {busy && <Loader2 className="h-4 w-4 animate-spin" />}
          确认删除
        </button>
      </div>
    </Modal>
  )
}
