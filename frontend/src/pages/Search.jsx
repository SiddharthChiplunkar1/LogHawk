import { useState, useRef } from 'react'
import { searchLogs, compareSearch } from '../api/loghawk'

function escapeHtml(str) {
  if (!str) return ''
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast ${type}`}>{msg}</div>
}

export default function Search() {
  const [keyword, setKeyword] = useState('')
  const [timeFrom, setTimeFrom] = useState('')
  const [timeTo, setTimeTo] = useState('')
  const [loading, setLoading] = useState(false)
  const [results, setResults] = useState(null)
  const [comparing, setComparing] = useState(false)
  const [comparison, setComparison] = useState(null)
  const [toast, setToast] = useState({ msg: '', type: 'info' })
  const lastResultsRef = useRef([])

  const showToast = (msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg: '', type: 'info' }), 3000)
  }

  const handleSearch = async () => {
    if (!keyword.trim()) { showToast('Please enter a search query', 'error'); return }

    setLoading(true)
    setResults(null)
    setComparison(null)
    try {
      const body = { keyword: keyword.trim(), maxResults: 500 }
      if (timeFrom) body.startTime = new Date(timeFrom).getTime()
      if (timeTo) body.endTime = new Date(timeTo).getTime()

      const data = await searchLogs(body)
      setResults(data)
      lastResultsRef.current = data.entries || []
    } catch (e) {
      showToast('Search failed: ' + e.message, 'error')
    } finally {
      setLoading(false)
    }
  }

  const handleCompare = async () => {
    if (!keyword.trim()) { showToast('Please enter a keyword first', 'error'); return }

    setComparing(true)
    setComparison(null)
    try {
      const data = await compareSearch(keyword.trim())
      setComparison(data)
    } catch (e) {
      showToast('Compare failed: ' + e.message, 'error')
    } finally {
      setComparing(false)
    }
  }

  const handleExport = () => {
    const rows = lastResultsRef.current
    if (!rows.length) { showToast('No results to export', 'error'); return }
    let csv = 'Timestamp,Level,Source,Message\n'
    rows.forEach(e => {
      csv += `"${e.formattedTimestamp || e.timestamp}","${e.level}","${e.sourceFile || ''}","${(e.message || '').replace(/"/g, '""')}"\n`
    })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
    a.download = 'loghawk-results.csv'
    a.click()
    showToast('Exported as CSV', 'success')
  }

  const handleClear = () => {
    setKeyword(''); setTimeFrom(''); setTimeTo('')
    setResults(null); setComparison(null)
    lastResultsRef.current = []
  }

  const agg = comparison?.aggregations || {}
  const linearMs = agg.linearSearchTimeMs || 0
  const indexedMs = agg.indexedSearchTimeMs || 1
  const pct = Math.min(100, (indexedMs / Math.max(linearMs, 1)) * 100)

  return (
    <>
      <Toast {...toast} />

      {/* Search form */}
      <div className="card">
        <div className="card-body">
          <div className="form-row mb-16">
            <div className="form-group flex-grow">
              <label>Search Query</label>
              <input
                className="form-control"
                placeholder="ERROR, OutOfMemory, exception…"
                value={keyword}
                onChange={e => setKeyword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleSearch()}
              />
            </div>
          </div>
          <div className="form-row">
            <div className="form-group">
              <label>From</label>
              <input type="datetime-local" className="form-control" value={timeFrom} onChange={e => setTimeFrom(e.target.value)} />
            </div>
            <div className="form-group">
              <label>To</label>
              <input type="datetime-local" className="form-control" value={timeTo} onChange={e => setTimeTo(e.target.value)} />
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
              <button className="btn btn-primary" onClick={handleSearch} disabled={loading}>
                {loading ? <span className="spinner" /> : '🔍'} Search
              </button>
              <button className="btn btn-secondary" onClick={handleCompare} disabled={comparing}>
                {comparing ? <span className="spinner" /> : '⚡'} Compare Methods
              </button>
              <button className="btn btn-secondary" onClick={handleExport}>⬇ CSV</button>
              <button className="btn btn-secondary" onClick={handleClear}>✕ Clear</button>
            </div>
          </div>
        </div>
      </div>

      {/* Results */}
      {results && (
        <>
          <div className="search-meta">
            <span>Found <strong>{(results.totalMatches || 0).toLocaleString()}</strong> matches</span>
            <span className="query-time">{(results.queryTimeMs || 0).toFixed(2)} ms</span>
            <span>across <strong>{results.shardsSearched || 0}</strong> shards</span>
            {results.aggregations && Object.entries(results.aggregations).map(([k, v]) => (
              <span key={k} className="text-secondary">{k}: <strong style={{ color: 'var(--text-primary)' }}>{v}</strong></span>
            ))}
          </div>

          <div className="card">
            <div className="card-header">
              <h3>Results ({(results.returnedMatches || 0).toLocaleString()} shown)</h3>
            </div>
            <div className="table-container">
              <table>
                <thead>
                  <tr><th>Timestamp</th><th>Level</th><th>Source</th><th>Message</th></tr>
                </thead>
                <tbody>
                  {results.entries?.length === 0 && (
                    <tr><td colSpan="4" className="empty-state">No matching logs found.</td></tr>
                  )}
                  {results.entries?.map((e, i) => (
                    <tr key={i}>
                      <td className="col-ts">{e.formattedTimestamp || new Date(e.timestamp).toLocaleString()}</td>
                      <td><span className={`log-level ${e.level}`}>{e.level}</span></td>
                      <td className="text-secondary" style={{ fontSize: 12 }}>{e.sourceFile || e.thread || '—'}</td>
                      <td className="col-msg" title={e.message}>{e.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* Compare results */}
      {comparison && (
        <div className="card">
          <div className="card-header"><h3>Search Method Comparison</h3></div>
          <div className="card-body">
            <div className="compare-bar-wrapper">
              <span className="compare-label">Linear Search</span>
              <div className="compare-track">
                <div className="compare-fill naive" style={{ width: '100%' }} />
              </div>
              <span className="compare-val">{linearMs.toFixed(2)} ms</span>
            </div>
            <div className="compare-bar-wrapper">
              <span className="compare-label">Indexed Search</span>
              <div className="compare-track">
                <div className="compare-fill optimized" style={{ width: `${pct}%` }} />
              </div>
              <span className="compare-val">{indexedMs.toFixed(2)} ms</span>
            </div>
            <p style={{ textAlign: 'center', marginTop: 16 }}>
              Speedup: <strong className="text-success">{agg.speedupFactor || 'N/A'}</strong>
            </p>
          </div>
        </div>
      )}
    </>
  )
}
