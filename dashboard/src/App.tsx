import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import Layout from '@/components/Layout'
import ToastHost from '@/components/Toast'
import LoginPage from '@/pages/LoginPage'
import OverviewPage from '@/pages/OverviewPage'
import ChannelsPage from '@/pages/ChannelsPage'
import ChannelDetailPage from '@/pages/ChannelDetailPage'
import AgentsPage from '@/pages/AgentsPage'
import AccountsPage from '@/pages/AccountsPage'
import { getStoredUsername } from '@/api/client'
import type { ReactNode } from 'react'

/** 受保护路由：未登录重定向到登录页 */
function RequireAuth({ children }: { children: ReactNode }) {
  if (!getStoredUsername()) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}

export default function App() {
  return (
    <Router>
      <ToastHost />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/overview" replace />} />
          <Route path="/overview" element={<OverviewPage />} />
          <Route path="/channels" element={<ChannelsPage />} />
          <Route path="/channels/:channelId" element={<ChannelDetailPage />} />
          <Route path="/agents" element={<AgentsPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/overview" replace />} />
      </Routes>
    </Router>
  )
}
