import { useCallback, useState } from 'react'

export type Theme = 'dark' | 'light'

const STORAGE_KEY = 'acp-relay-theme'

function getInitialTheme(): Theme {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return 'dark'
}

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(getInitialTheme)

  const apply = useCallback((next: Theme) => {
    setTheme(next)
    document.documentElement.classList.toggle('dark', next === 'dark')
    document.documentElement.classList.toggle('light', next === 'light')
    localStorage.setItem(STORAGE_KEY, next)
  }, [])

  const toggle = useCallback(() => {
    apply(theme === 'dark' ? 'light' : 'dark')
  }, [theme, apply])

  return { theme, toggle, apply }
}
