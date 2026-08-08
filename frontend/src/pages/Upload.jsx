import { useState, useRef } from 'react'
import { uploadFile, uploadFileAsync, fetchIngestionStatus } from '../api/loghawk'

function Toast({ msg, type }) {
  if (!msg) return null
  return <div className={`toast ${type}`}>{msg}</div>
}

const fmt = n => Number(n || 0).toLocaleString()

export default function Upload() {
  const [file, setFile] = useState(null)
  const [format, setFormat] = useState('SIMPLE')
  const [dragging, setDragging] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [result, setResult] = useState(null)
  const [status, setStatus] = useState(null)
  const [toast, setToast] = useState({ msg: '', type: 'info' })
  const inputRef = useRef(null)

  const showToast = (msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast({ msg: '', type: 'info' }), 3500)
  }

  const pickFile = (f) => {
    if (f) setFile(f)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragging(false)
    const f = e.dataTransfer.files[0]
    if (f) pickFile(f)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!file) return

    setUploading(true)
    setProgress(0)
    setResult(null)

    // Simulated progress
    let pct = 0
    const iv = setInterval(() => {
      pct = Math.min(90, pct + 6)
      setProgress(pct)
    }, 200)

    try {
      const fd = new FormData()
      fd.append('file', file)
      fd.append('format', format)

      const data = await uploadFile(fd)

      clearInterval(iv)
      setProgress(100)
      setResult({ success: true, ...data })
      showToast('Upload & ingestion complete!', 'success')
    } catch (e) {
      clearInterval(iv)
      setResult({ success: false, error: e.message })
      showToast('Upload failed: ' + e.message, 'error')
    } finally {
      setUploading(false)
    }
  }

  const handleAsyncUpload = async () => {
    if (!file) { showToast('Please select a file first', 'error'); return }

    const fd = new FormData()
    fd.append('file', file)
    fd.append('format', format)
    try {
      const data = await uploadFileAsync(fd)
      showToast(data.message || 'Async ingestion started', 'info')
    } catch (e) {
      showToast('Async upload failed: ' + e.message, 'error')
    }
  }

  const checkStatus = async () => {
    try {
      const data = await fetchIngestionStatus()
      setStatus(data)
    } catch (e) {
      showToast('Status check failed', 'error')
    }
  }

  return (
    <>
      <Toast {...toast} />

      <div className="card">
        <div className="card-header">
          <h3>Ingest New Logs</h3>
          <button className="btn btn-secondary btn-sm" onClick={checkStatus}>Check Ingestion Status</button>
        </div>
        <div className="card-body">
          {status && (
            <div style={{
              marginBottom: 16, padding: '8px 12px', borderRadius: 6,
              background: status.isIngesting ? 'rgba(56,139,253,.1)' : 'rgba(63,185,80,.1)',
              border: `1px solid ${status.isIngesting ? 'var(--accent)' : 'var(--success)'}`,
              color: status.isIngesting ? 'var(--accent-hover)' : 'var(--success)',
              fontSize: 13
            }}>
              {status.isIngesting ? '⏳ Ingestion in progress…' : '✅ No active ingestion.'}
            </div>
          )}

          {/* Format picker */}
          <div className="form-group" style={{ marginBottom: 20, maxWidth: 260 }}>
            <label>Log Format</label>
            <select className="form-control" value={format} onChange={e => setFormat(e.target.value)}>
              <option value="SIMPLE">Simple (LogHawk Default)</option>
              <option value="JSON">JSON (Structured)</option>
            </select>
          </div>

          {/* Drop zone / file info */}
          {!file ? (
            <div
              className={`drop-zone ${dragging ? 'dragover' : ''}`}
              onDragEnter={e => { e.preventDefault(); setDragging(true) }}
              onDragOver={e => { e.preventDefault(); setDragging(true) }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
              onClick={() => inputRef.current?.click()}
            >
              <span className="drop-icon">☁</span>
              <span className="drop-text">Drag &amp; drop your log file here</span>
              <span className="drop-sub">or click to browse</span>
              <input
                ref={inputRef}
                type="file"
                accept=".log,.txt,.json"
                style={{ display: 'none' }}
                onChange={e => pickFile(e.target.files[0])}
              />
            </div>
          ) : (
            <div className="file-info" style={{ marginBottom: 16 }}>
              <span>📄</span>
              <span className="file-name">
                {file.name} — <span className="text-secondary">{(file.size / 1024 / 1024).toFixed(2)} MB</span>
              </span>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => { setFile(null); setResult(null); setProgress(0) }}
              >✕ Remove</button>
            </div>
          )}

          {/* Actions */}
          <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
            <button
              className="btn btn-primary"
              disabled={!file || uploading}
              onClick={handleSubmit}
            >
              {uploading ? <><span className="spinner" /> Uploading…</> : '⬆ Upload & Ingest'}
            </button>
            <button
              className="btn btn-secondary"
              disabled={!file || uploading}
              onClick={handleAsyncUpload}
            >
              ⚡ Async Ingest
            </button>
          </div>

          {/* Progress bar */}
          {uploading && (
            <div style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                <span className="text-secondary">Uploading…</span>
                <span>{progress}%</span>
              </div>
              <div className="progress-bar-wrap">
                <div className="progress-bar" style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}

          {/* Result */}
          {result && (
            <div className="upload-result">
              {result.success ? (
                <>
                  <p className="result-success" style={{ marginBottom: 12 }}>✅ Upload &amp; ingestion complete!</p>
                  <div className="result-grid">
                    {[
                      ['Lines Processed', fmt(result.linesProcessed)],
                      ['Bytes Processed', fmt(result.bytesProcessed)],
                      ['Duration', `${(result.durationSeconds || 0).toFixed(2)} s`],
                      ['Throughput', `${(result.throughputMBps || 0).toFixed(2)} MB/s`],
                    ].map(([label, value]) => (
                      <div className="result-item" key={label}>
                        <div className="ri-label">{label}</div>
                        <div className="ri-value">{value}</div>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <p className="result-error">❌ Error: {result.error}</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Instructions */}
      <div className="card">
        <div className="card-header"><h3>ℹ Supported Formats</h3></div>
        <div className="card-body">
          <table>
            <thead>
              <tr><th>Format</th><th>Example Line</th></tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>SIMPLE</strong></td>
                <td className="mono text-secondary" style={{ fontSize: 12 }}>2024-01-15T10:30:00 INFO [main] com.example.App - Application started</td>
              </tr>
              <tr>
                <td><strong>JSON</strong></td>
                <td className="mono text-secondary" style={{ fontSize: 12 }}>{`{"timestamp":"…","level":"ERROR","message":"…"}`}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}
