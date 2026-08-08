import { useState, useRef, useCallback } from 'react'

const LEVELS = ['INFO', 'WARN', 'ERROR', 'DEBUG']

export default function LiveFeed() {
  const [connected, setConnected] = useState(false)
  const [filters, setFilters] = useState({ INFO: true, WARN: true, ERROR: true, DEBUG: true })
  const [lines, setLines] = useState([])
  const [msgCount, setMsgCount] = useState(0)
  const wsRef = useRef(null)
  const feedRef = useRef(null)

  const escapeHtml = (str) => {
    if (!str) return ''
    const d = document.createElement('div')
    d.textContent = str
    return d.innerHTML
  }

  const connect = useCallback(() => {
    if (wsRef.current) return
    const ws = new WebSocket(`ws://${window.location.hostname}:8080/api/v1/stream`)
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      addLine({ level: 'INFO', timestamp: new Date().toISOString(), message: 'Connected to Live Feed.' })
    }

    ws.onmessage = (event) => {
      try {
        const entry = JSON.parse(event.data)
        setMsgCount(c => c + 1)
        addLine(entry)
      } catch { /* ignore parse errors */ }
    }

    ws.onerror = () => {
      addLine({ level: 'ERROR', timestamp: new Date().toISOString(), message: 'WebSocket error.' })
    }

    ws.onclose = () => {
      setConnected(false)
      wsRef.current = null
      addLine({ level: 'WARN', timestamp: new Date().toISOString(), message: 'Disconnected from Live Feed.' })
    }
  }, [])

  const disconnect = () => {
    wsRef.current?.close()
  }

  const clearFeed = () => {
    setLines([])
    setMsgCount(0)
  }

  const addLine = (entry) => {
    setLines(prev => {
      const next = [...prev, entry]
      return next.length > 1000 ? next.slice(-1000) : next
    })
    // Auto-scroll
    setTimeout(() => {
      if (feedRef.current) feedRef.current.scrollTop = feedRef.current.scrollHeight
    }, 0)
  }

  const toggleFilter = (level) => {
    setFilters(f => ({ ...f, [level]: !f[level] }))
  }

  const visibleLines = lines.filter(l => filters[l.level] !== false)

  return (
    <>
      {/* Controls */}
      <div className="card">
        <div className="card-body">
          <div className="feed-controls">
            <button
              className="btn btn-primary"
              onClick={connect}
              disabled={connected}
            >🔌 Connect</button>
            <button
              className="btn btn-secondary"
              onClick={disconnect}
              disabled={!connected}
            >✕ Disconnect</button>
            <button className="btn btn-secondary" onClick={clearFeed}>🗑 Clear</button>

            <div className="status-indicator" style={{ marginLeft: 8 }}>
              <span className={`status-dot ${connected ? 'online' : ''}`} />
              <span>{connected ? 'Connected' : 'Disconnected'}</span>
            </div>

            <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--text-secondary)' }}>
              {msgCount.toLocaleString()} messages received
            </span>
          </div>

          {/* Level filters */}
          <div className="filter-group" style={{ marginTop: 12 }}>
            {LEVELS.map(level => (
              <label key={level} className="filter-label" style={{ marginRight: 16 }}>
                <input
                  type="checkbox"
                  checked={filters[level]}
                  onChange={() => toggleFilter(level)}
                  style={{ marginRight: 4 }}
                />
                <span className={`log-level ${level}`}>{level}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      {/* Feed */}
      <div className="live-feed" ref={feedRef}>
        {visibleLines.length === 0 && (
          <div style={{ color: 'var(--text-muted)', padding: 8 }}>
            {connected ? 'Waiting for log messages…' : 'Click Connect to start streaming.'}
          </div>
        )}
        {visibleLines.map((line, i) => {
          const ts = line.formattedTimestamp || line.timestamp || ''
          return (
            <div key={i} className={`live-line ${line.level?.toLowerCase()}`}>
              <span className="live-ts">[{ts}]</span>
              <span className={`log-level ${line.level}`} style={{ flexShrink: 0 }}>{line.level}</span>
              <span className="live-msg">{line.message}</span>
            </div>
          )
        })}
      </div>

      <p className="text-muted" style={{ marginTop: 8, fontSize: 12 }}>
        ℹ WebSocket endpoint: <span className="mono">ws://localhost:8080/api/v1/stream</span>
        &nbsp;— requires the Spring Boot app to expose a WebSocket handler at that path.
      </p>
    </>
  )
}
