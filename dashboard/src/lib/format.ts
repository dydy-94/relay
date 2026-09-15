/** 格式化时间戳（毫秒）为本地时间字符串 */
export function formatTime(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms === 0) return '—'
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 相对时间（如 3 分钟前） */
export function formatRelative(ms: number | null | undefined): string {
  if (ms === null || ms === undefined || ms === 0) return '—'
  const diff = Date.now() - ms
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return `${sec} 秒前`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  return formatTime(ms)
}

/** 千分位数字 */
export function formatNumber(n: number): string {
  return n.toLocaleString('en-US')
}
