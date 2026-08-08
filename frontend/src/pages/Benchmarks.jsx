import { useState } from 'react'
import {
  runAllBenchmarks,
  runLinearVsIndexed,
  runConcurrency,
  runTimeRange,
} from '../api/loghawk'

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast ${type}`}>{msg}</div>
}

function LinearTable({ data }) {
  if (!data?.comparisons) return null
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr>
            <th>Keyword</th>
            <th>Linear (ms)</th>
            <th>Indexed (ms)</th>
            <th>Speedup</th>
            <th>Matches</th>
          </tr>
        </thead>
        <tbody>
          {data.comparisons.map((c, i) => (
            <tr key={i}>
              <td><strong>{c.keyword}</strong></td>
              <td className="col-bad">{(c.linearTimeMs || 0).toFixed(2)}</td>
              <td className="col-good">{(c.indexedTimeMs || 0).toFixed(2)}</td>
              <td><strong className="text-success">{c.speedup || 'N/A'}</strong></td>
              <td>{(c.matches || 0).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="text-muted" style={{ padding: '8px 14px' }}>
        Total indexed: <strong style={{ color: 'var(--text-primary)' }}>{(data.totalEntries || 0).toLocaleString()}</strong> entries
      </p>
    </div>
  )
}

function ConcurrencyTable({ data }) {
  if (!data?.scalingData) return null
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr><th>Threads</th><th>Total Time (ms)</th><th>Avg / Query (ms)</th><th>Throughput (q/s)</th></tr>
        </thead>
        <tbody>
          {data.scalingData.map((d, i) => (
            <tr key={i}>
              <td><strong>{d.threads}</strong></td>
              <td>{(d.totalTimeMs || 0).toFixed(2)}</td>
              <td>{(d.averageTimeMs || 0).toFixed(2)}</td>
              <td className="col-good">{(d.throughput || 0).toFixed(1)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function TimeRangeTable({ data }) {
  if (!data?.rangeResults) return null
  return (
    <div className="table-container">
      <table>
        <thead>
          <tr><th>Range</th><th>Duration (hours)</th><th>Avg Time (ms)</th><th>Matches</th></tr>
        </thead>
        <tbody>
          {data.rangeResults.map((r, i) => (
            <tr key={i}>
              <td><strong>{r.rangePercentage || '—'}</strong></td>
              <td>{(r.rangeHours || 0).toFixed(1)}</td>
              <td>{(r.averageTimeMs || 0).toFixed(2)}</td>
              <td>{(r.matches || 0).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function SysInfo({ data }) {
  if (!data) return null
  return (
    <div className="sysinfo-grid section-gap">
      {[
        ['Processors', data.availableProcessors],
        ['Max Memory', `${data.maxMemoryMB || '—'} MB`],
        ['Java', data.javaVersion],
        ['OS', data.osName],
      ].map(([k, v]) => (
        <div className="sysinfo-item" key={k}>
          <div className="si-label">{k}</div>
          <div className="si-value">{v || '—'}</div>
        </div>
      ))}
    </div>
  )
}

export default function Benchmarks() {
  const [loading, setLoading] = useState(null) // which benchmark is running
  const [linear, setLinear] = useState(null)
  const [concur, setConcur] = useState(null)
  const [timeR, setTimeR] = useState(null)
  const [sysInfo, setSysInfo] = useState(null)
  const [toast, setToast] = useState({ msg: '', type: 'info' })

  const showToast = (msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg: '', type: 'info' }), 3000)
  }

  const run = async (name, apiFn, ...setters) => {
    if (loading) return
    setLoading(name)
    try {
      const data = await apiFn()
      setters.forEach(([fn, key]) => key ? fn(data[key]) : fn(data))
      showToast('Benchmark complete!', 'success')
    } catch (e) {
      showToast('Benchmark failed: ' + e.message, 'error')
    } finally {
      setLoading(null)
    }
  }

  const runAll = () => run('all', runAllBenchmarks,
    [setLinear, 'linearVsIndexed'],
    [setConcur, 'concurrencyScaling'],
    [setTimeR, 'timeRangeSearch'],
    [setSysInfo, 'systemInfo'],
  )

  const runLv = () => run('linear', runLinearVsIndexed, [setLinear, null])
  const runCc = () => run('concur', runConcurrency, [setConcur, null])
  const runTr = () => run('time', runTimeRange, [setTimeR, null])

  return (
    <>
      <Toast {...toast} />

      {/* Action bar */}
      <div className="benchmark-actions">
        <button className="btn btn-primary" onClick={runAll} disabled={!!loading}>
          {loading === 'all' ? <><span className="spinner" /> Running All…</> : '▶ Run Full Benchmark'}
        </button>
        <button className="btn btn-secondary" onClick={runLv} disabled={!!loading}>
          {loading === 'linear' ? <span className="spinner" /> : null} Linear vs Indexed
        </button>
        <button className="btn btn-secondary" onClick={runCc} disabled={!!loading}>
          {loading === 'concur' ? <span className="spinner" /> : null} Concurrency Scaling
        </button>
        <button className="btn btn-secondary" onClick={runTr} disabled={!!loading}>
          {loading === 'time' ? <span className="spinner" /> : null} Time-Range Search
        </button>
      </div>

      {/* Linear vs Indexed */}
      <div className="card">
        <div className="card-header"><h3>Linear Search vs Indexed Search</h3></div>
        {linear
          ? <LinearTable data={linear} />
          : <p className="text-muted section-gap">Run benchmarks to see results.</p>}
      </div>

      {/* Concurrency */}
      <div className="card">
        <div className="card-header"><h3>Concurrency Scaling</h3></div>
        {concur
          ? <ConcurrencyTable data={concur} />
          : <p className="text-muted section-gap">Run benchmarks to see results.</p>}
      </div>

      {/* Time-Range */}
      <div className="card">
        <div className="card-header"><h3>Time-Range Search Performance</h3></div>
        {timeR
          ? <TimeRangeTable data={timeR} />
          : <p className="text-muted section-gap">Run benchmarks to see results.</p>}
      </div>

      {/* Big-O Table — always visible */}
      <div className="card">
        <div className="card-header"><h3>📐 Big-O Complexity Analysis</h3></div>
        <div className="table-container">
          <table>
            <thead>
              <tr><th>Operation</th><th>Naïve</th><th>Optimized</th><th>Explanation</th></tr>
            </thead>
            <tbody>
              {[
                ['Keyword Search', 'O(n)', 'O(1)', 'Inverted index eliminates full scan'],
                ['Time-Range Filter', 'O(n)', 'O(log n)', 'Binary search on sorted timestamps'],
                ['Multi-Keyword AND', 'O(n × k)', 'O(min results)', 'Intersection of posting lists'],
                ['Level Aggregation', 'O(n)', 'O(1)', 'Pre-computed counters'],
                ['Ingestion (k threads)', 'O(n)', 'O(n/k)', 'Parallel chunk processing'],
              ].map(([op, naive, opt, exp]) => (
                <tr key={op}>
                  <td>{op}</td>
                  <td className="col-bad mono">{naive}</td>
                  <td className="col-good mono">{opt}</td>
                  <td className="text-secondary">{exp}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* System Info */}
      <div className="card">
        <div className="card-header"><h3>💻 System Information</h3></div>
        {sysInfo
          ? <SysInfo data={sysInfo} />
          : <p className="text-muted section-gap">Run benchmarks to populate.</p>}
      </div>
    </>
  )
}
