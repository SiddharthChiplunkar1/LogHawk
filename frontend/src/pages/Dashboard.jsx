import { useEffect, useState, useCallback } from 'react'
import { fetchStats, searchLogs } from '../api/loghawk'

const fmt = (n) => (Number(n) || 0).toLocaleString()

function StatCard({ label, value, colorClass = '' }) {
  return (
    <div className={`stat-card ${colorClass}`}>
      <div className="stat-info">
        <span className="stat-label">{label}</span>
        <span className={`stat-value`}>{value}</span>
      </div>
    </div>
  )
}

function LevelBars({ dist }) {
  if (!dist) return null
  const total = Object.values(dist).reduce((a, b) => a + b, 0) || 1
  return (
    <>
      {Object.entries(dist).map(([level, count]) => (
        <div className="bar-item" key={level}>
          <span className="bar-label">{level}</span>
          <div className="bar">
            <div
              className={`bar-fill ${level.toLowerCase()}`}
              style={{ width: `${(count / total) * 100}%` }}
            />
          </div>
          <span className="bar-count">{fmt(count)}</span>
        </div>
      ))}
    </>
  )
}

export default function Dashboard() {
  const [stats, setStats] = useState(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState(null)
  const [searching, setSearching] = useState(false)

  const loadStats = useCallback(() => {
    fetchStats().then(setStats).catch(console.error)
  }, [])

  useEffect(() => {
    loadStats()
    const iv = setInterval(loadStats, 10000)
    return () => clearInterval(iv)
  }, [loadStats])

  const handleQuickSearch = async () => {
    if (!query.trim()) return
    setSearching(true)
    try {
      const data = await searchLogs({ keyword: query, maxResults: 10 })
      setResults(data)
    } catch (e) {
      setResults({ error: e.message })
    } finally {
      setSearching(false)
    }
  }

  const coord = stats?.coordinator || {}

  return (
    <>
      {/* Stat cards */}
      <div className="stat-grid">
        <StatCard label="Total Logs Indexed" value={fmt(stats?.totalEntries)} colorClass="primary" />
        <StatCard label="Active Shards" value={coord.totalShards ?? '—'} colorClass="success" />
        <StatCard label="Index Terms" value={fmt(stats?.indexTerms)} colorClass="warning" />
        <StatCard label="Active Threads" value={coord.activeThreads ?? '—'} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 20 }}>
        {/* Level distribution */}
        <div className="card">
          <div className="card-header">
            <h3>Log Level Distribution</h3>
            <button className="btn btn-secondary btn-sm" onClick={loadStats}>Refresh</button>
          </div>
          <div className="card-body">
            {stats?.levelDistribution
              ? <LevelBars dist={stats.levelDistribution} />
              : <p className="text-muted">Loading…</p>
            }
          </div>
        </div>

        {/* System info */}
        <div className="card">
          <div className="card-header"><h3>System Info</h3></div>
          <div className="card-body">
            {stats ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {[
                  ['JVM Memory Used', `${stats.jvmMemoryUsedMB || 0} MB`],
                  ['JVM Memory Total', `${stats.jvmMemoryTotalMB || 0} MB`],
                  ['Processors', stats.availableProcessors || '—'],
                  ['Completed Tasks', fmt(coord.completedTasks)],
                  ['Oldest Log', stats.oldestEntry || 'N/A'],
                  ['Newest Log', stats.newestEntry || 'N/A'],
                ].map(([k, v]) => (
                  <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                    <span className="text-secondary">{k}</span>
                    <span className="mono" style={{ color: 'var(--text-primary)' }}>{v}</span>
                  </div>
                ))}
              </div>
            ) : <p className="text-muted">Loading…</p>}
          </div>
        </div>
      </div>

      {/* Quick Search */}
      <div className="card" style={{ marginTop: 20 }}>
        <div className="card-header"><h3>Quick Search</h3></div>
        <div className="card-body">
          <div className="form-row mb-16">
            <div className="form-group flex-grow">
              <input
                className="form-control"
                placeholder="Type a keyword and press Enter (e.g. ERROR, OutOfMemory)"
                value={query}
                onChange={e => setQuery(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleQuickSearch()}
              />
            </div>
            <button className="btn btn-primary" onClick={handleQuickSearch} disabled={searching}>
              {searching ? <span className="spinner" /> : null} Search
            </button>
          </div>

          {results?.error && <p className="text-danger">Error: {results.error}</p>}
          {results && !results.error && (
            <>
              <p className="text-secondary" style={{ marginBottom: 8, fontSize: 13 }}>
                Found <strong style={{ color: 'var(--text-primary)' }}>{fmt(results.totalMatches)}</strong> matches
                in <span className="query-time">{results.queryTimeMs?.toFixed(2)} ms</span>
              </p>
              {results.entries?.length > 0 ? (
                <div className="table-container">
                  <table>
                    <thead>
                      <tr><th>Timestamp</th><th>Level</th><th>Message</th></tr>
                    </thead>
                    <tbody>
                      {results.entries.slice(0, 5).map((e, i) => (
                        <tr key={i}>
                          <td className="col-ts">{e.formattedTimestamp || new Date(e.timestamp).toLocaleString()}</td>
                          <td><span className={`log-level ${e.level}`}>{e.level}</span></td>
                          <td className="col-msg">{e.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : <p className="text-muted">No results found.</p>}
            </>
          )}
        </div>
      </div>
    </>
  )
}
