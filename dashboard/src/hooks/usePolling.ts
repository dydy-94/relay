import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 通用轮询 hook：立即执行一次 + 按 interval 周期刷新.
 * 组件卸载或依赖变化时自动清理；竞态防护：仅采纳最新一次请求的结果.
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs: number,
): {
  data: T | null
  error: string | null
  loading: boolean
  refresh: () => void
} {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher
  const aliveRef = useRef(true)
  const seqRef = useRef(0)

  const load = useCallback(async () => {
    const seq = ++seqRef.current
    try {
      const result = await fetcherRef.current()
      if (aliveRef.current && seq === seqRef.current) {
        setData(result)
        setError(null)
      }
    } catch (e) {
      if (aliveRef.current && seq === seqRef.current) {
        setError(e instanceof Error ? e.message : '加载失败')
      }
    } finally {
      if (aliveRef.current && seq === seqRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    aliveRef.current = true
    load()
    const timer = setInterval(load, intervalMs)
    return () => {
      aliveRef.current = false
      clearInterval(timer)
    }
  }, [load, intervalMs])

  return { data, error, loading, refresh: load }
}
