/**
 * LogHawk API client — integrates all REST endpoints from LogHawkController
 */

const BASE = '/api/v1';

async function request(url, options = {}) {
  const res = await fetch(url, options);
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  return res.json();
}

// ── Health ──────────────────────────────────────────────────────────────────
export const fetchHealth = () => request(`${BASE}/health`);

// ── Stats ───────────────────────────────────────────────────────────────────
export const fetchStats = () => request(`${BASE}/stats`);

// ── Search ──────────────────────────────────────────────────────────────────
/**
 * @param {{ keyword?: string, startTime?: number, endTime?: number, levels?: string[], maxResults?: number }} body
 */
export const searchLogs = (body) =>
  request(`${BASE}/search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });

// ── Compare search methods ───────────────────────────────────────────────────
export const compareSearch = (keyword) =>
  request(`${BASE}/compare?keyword=${encodeURIComponent(keyword)}`);

// ── Benchmarks ───────────────────────────────────────────────────────────────
export const runAllBenchmarks = () => request(`${BASE}/benchmarks/run`);
export const runLinearVsIndexed = () => request(`${BASE}/benchmarks/linear-vs-indexed`);
export const runConcurrency = () => request(`${BASE}/benchmarks/concurrency`);
export const runTimeRange = () => request(`${BASE}/benchmarks/time-range`);

// ── Ingestion ─────────────────────────────────────────────────────────────────
/**
 * @param {FormData} formData — should contain 'file' and 'format'
 */
export const uploadFile = (formData) =>
  request(`${BASE}/ingestion/upload`, { method: 'POST', body: formData });

export const uploadFileAsync = (formData) =>
  request(`${BASE}/ingestion/upload-async`, { method: 'POST', body: formData });

export const fetchIngestionStatus = () => request(`${BASE}/ingestion/status`);
