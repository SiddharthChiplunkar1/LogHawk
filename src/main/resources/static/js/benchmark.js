let benchmarkInProgress = false;

async function runBenchmark() {
    if (benchmarkInProgress) {
        showToast('Benchmark already running...', 'error');
        return;
    }

    benchmarkInProgress = true;

    // Show loading state
    const btn = document.querySelector('.btn-primary');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span> Running...';
    }

    try {
        const response = await fetch('/api/v1/benchmarks/run');
        if (!response.ok) throw new Error('Benchmark failed');

        const data = await response.json();
        renderBenchmarkResults(data);
        showToast('Benchmarks complete!', 'success');

    } catch (err) {
        console.error('Benchmark error:', err);
        showToast('Benchmark failed: ' + err.message, 'error');
    } finally {
        benchmarkInProgress = false;
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '▶ Run Full Benchmark';
        }
    }
}

async function runLinearVsIndexed() {
    try {
        const response = await fetch('/api/v1/benchmarks/linear-vs-indexed');
        if (!response.ok) throw new Error('Benchmark failed');
        const data = await response.json();
        renderLinearVsIndexed(data);
    } catch (err) {
        showToast('Benchmark failed: ' + err.message, 'error');
    }
}

async function runConcurrencyScaling() {
    try {
        const response = await fetch('/api/v1/benchmarks/concurrency');
        if (!response.ok) throw new Error('Benchmark failed');
        const data = await response.json();
        renderConcurrencyScaling(data);
    } catch (err) {
        showToast('Benchmark failed: ' + err.message, 'error');
    }
}

function renderBenchmarkResults(data) {
    // Render linear vs indexed
    if (data.linearVsIndexed) {
        renderLinearVsIndexed(data.linearVsIndexed);
    }

    // Render concurrency scaling
    if (data.concurrencyScaling) {
        renderConcurrencyScaling(data.concurrencyScaling);
    }

    // Render time range
    if (data.timeRangeSearch) {
        renderTimeRange(data.timeRangeSearch);
    }

    // Render system info
    if (data.systemInfo) {
        renderSystemInfo(data.systemInfo);
    }
}

function renderLinearVsIndexed(data) {
    const container = document.getElementById('linearVsIndexed');
    if (!container || !data.comparisons) return;

    let html = '<div class="table-container"><table class="benchmark-table">';
    html += '<thead><tr><th>Keyword</th><th>Linear (ms)</th><th>Indexed (ms)</th><th>Speedup</th><th>Matches</th></tr></thead><tbody>';

    data.comparisons.forEach(c => {
        html += `<tr>
            <td><strong>${escapeHtml(c.keyword)}</strong></td>
            <td>${(c.linearTimeMs || 0).toFixed(2)}</td>
            <td class="good">${(c.indexedTimeMs || 0).toFixed(2)}</td>
            <td><strong>${c.speedup || 'N/A'}</strong></td>
            <td>${(c.matches || 0).toLocaleString()}</td>
        </tr>`;
    });

    html += '</tbody></table></div>';
    html += `<p style="margin-top: 0.5rem; color: var(--text-secondary); font-size: 0.85rem;">
        Total entries indexed: <strong>${(data.totalEntries || 0).toLocaleString()}</strong></p>`;

    container.innerHTML = html;
}

function renderConcurrencyScaling(data) {
    const container = document.getElementById('concurrencyScaling');
    if (!container || !data.scalingData) return;

    let html = '<div class="table-container"><table class="benchmark-table">';
    html += '<thead><tr><th>Threads</th><th>Total Time (ms)</th><th>Avg per Query (ms)</th><th>Throughput (queries/sec)</th></tr></thead><tbody>';

    data.scalingData.forEach(d => {
        html += `<tr>
            <td><strong>${d.threads}</strong></td>
            <td>${(d.totalTimeMs || 0).toFixed(2)}</td>
            <td>${(d.averageTimeMs || 0).toFixed(2)}</td>
            <td>${(d.throughput || 0).toFixed(1)}</td>
        </tr>`;
    });

    html += '</tbody></table></div>';
    container.innerHTML = html;
}

function renderTimeRange(data) {
    const container = document.getElementById('timeRangeSearch');
    if (!container || !data.rangeResults) return;

    let html = '<div class="table-container"><table class="benchmark-table">';
    html += '<thead><tr><th>Range</th><th>Duration (hours)</th><th>Avg Time (ms)</th><th>Matches</th></tr></thead><tbody>';

    data.rangeResults.forEach(r => {
        html += `<tr>
            <td><strong>${r.rangePercentage || '-'}</strong></td>
            <td>${(r.rangeHours || 0).toFixed(1)}</td>
            <td>${(r.averageTimeMs || 0).toFixed(2)}</td>
            <td>${(r.matches || 0).toLocaleString()}</td>
        </tr>`;
    });

    html += '</tbody></table></div>';
    container.innerHTML = html;
}

function renderSystemInfo(data) {
    const container = document.getElementById('systemInfo');
    if (!container) return;

    container.innerHTML = `
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.5rem;">
            <div><span style="color: var(--text-secondary);">Processors:</span> <strong>${data.availableProcessors || '-'}</strong></div>
            <div><span style="color: var(--text-secondary);">Max Memory:</span> <strong>${data.maxMemoryMB || '-'} MB</strong></div>
            <div><span style="color: var(--text-secondary);">Java:</span> <strong>${data.javaVersion || '-'}</strong></div>
            <div><span style="color: var(--text-secondary);">OS:</span> <strong>${data.osName || '-'}</strong></div>
        </div>
    `;
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function showToast(message, type) {
    const existing = document.querySelector('.toast');
    if (existing) existing.remove();

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(() => toast.remove(), 3000);
}