import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { fetchHealth } from './api/loghawk'
import Dashboard from './pages/Dashboard'
import Search from './pages/Search'
import Benchmarks from './pages/Benchmarks'
import Upload from './pages/Upload'
import LiveFeed from './pages/LiveFeed'

function Sidebar({ health }) {
  const nav = [
    { to: '/', icon: '⊞', label: 'Dashboard' },
    { to: '/search', icon: '🔍', label: 'Search Logs' },
    { to: '/benchmarks', icon: '📈', label: 'Benchmarks' },
    { to: '/upload', icon: '⬆', label: 'Upload Logs' },
    { to: '/live', icon: '📡', label: 'Live Feed' },
  ]
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <span className="brand-icon">🦅</span>
        <span className="brand-text">LogHawk</span>
      </div>
      <ul className="nav-links">
        {nav.map(n => (
          <li key={n.to}>
            <NavLink to={n.to} end={n.to === '/'} className={({ isActive }) => isActive ? 'active' : ''}>
              <span className="nav-icon">{n.icon}</span>
              <span>{n.label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </aside>
  )
}

function Topbar({ title, health }) {
  const up = health?.status === 'UP'
  return (
    <div className="topbar">
      <h2>{title}</h2>
      <div className="status-indicator">
        <span className={`status-dot ${up ? 'online' : 'offline'}`} />
        <span>{up ? `LogHawk v${health.version} — Online` : 'Checking status…'}</span>
      </div>
    </div>
  )
}

const PAGE_TITLES = {
  '/': 'Dashboard',
  '/search': 'Search Logs',
  '/benchmarks': 'Performance Benchmarks',
  '/upload': 'Upload Logs',
  '/live': 'Live Feed',
}

export default function App() {
  const [health, setHealth] = useState(null)

  useEffect(() => {
    fetchHealth().then(setHealth).catch(() => { })
    const iv = setInterval(() => fetchHealth().then(setHealth).catch(() => { }), 15000)
    return () => clearInterval(iv)
  }, [])

  return (
    <BrowserRouter>
      <div className="app-shell">
        <Sidebar health={health} />
        <div className="main-area">
          <Routes>
            {[
              { path: '/', element: <Dashboard />, title: 'Dashboard' },
              { path: '/search', element: <Search />, title: 'Search Logs' },
              { path: '/benchmarks', element: <Benchmarks />, title: 'Benchmarks' },
              { path: '/upload', element: <Upload />, title: 'Upload Logs' },
              { path: '/live', element: <LiveFeed />, title: 'Live Feed' },
            ].map(r => (
              <Route key={r.path} path={r.path} element={
                <>
                  <Topbar title={r.title} health={health} />
                  <div className="page-content">{r.element}</div>
                </>
              } />
            ))}
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}
