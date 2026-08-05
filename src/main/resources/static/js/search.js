let lastSearchResults = [];

async function executeSearch() {
    const keyword = document.getElementById('searchQuery')?.value.trim();
    const startTimeStr = document.getElementById('timeFrom')?.value;
    const endTimeStr = document.getElementById('timeTo')?.value;

    if (!keyword) {
        showToast('Please enter a search query', 'error');
        return;
    }

    const requestBody = { keyword: keyword, maxResults: 500 };

    if (startTimeStr) {
        requestBody.startTime = new Date(startTimeStr).getTime();
    }
    if (endTimeStr) {
        requestBody.endTime = new Date(endTimeStr).getTime();
    }

    // Show loading state
    const resultsBody = document.getElementById('resultsBody');
    if (resultsBody) {
        resultsBody.innerHTML = `<tr><td colspan="4" class="text-center">
            <span class="spinner"></span> Searching across shards...</td></tr>`;
    }

    const resultsCard = document.getElementById('resultsCard');
    if (resultsCard) resultsCard.style.display = 'block';

    const noResults = document.getElementById('noResults');
    if (noResults) noResults.style.display = 'none';

    try {
        const response = await fetch('/api/v1/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) throw new Error('Search failed');

        const data = await response.json();
        displayResults(data);
        lastSearchResults = data.entries || [];

    } catch (err) {
        console.error('Search error:', err);
        if (resultsBody) {
            resultsBody.innerHTML = `<tr><td colspan="4" class="text-center" style="color: var(--danger);">
                Search failed: ${err.message}</td></tr>`;
        }
    }
}

function displayResults(data) {
    // Update metadata
    const searchMeta = document.getElementById('searchMeta');
    if (searchMeta) searchMeta.style.display = 'flex';

    setText('resultCount', (data.totalMatches || 0).toLocaleString());
    setText('queryTime', (data.queryTimeMs || 0).toFixed(2) + ' ms');
    setText('shardsQueried', data.shardsSearched || 0);

    // Update aggregations
    const aggsContainer = document.getElementById('aggregations');
    if (aggsContainer && data.aggregations) {
        aggsContainer.innerHTML = '';
        for (const [key, value] of Object.entries(data.aggregations)) {
            aggsContainer.innerHTML += `<span class="agg-item">${key}: <strong>${value}</strong></span> `;
        }
    }

    // Populate table
    const resultsBody = document.getElementById('resultsBody');
    if (!resultsBody) return;

    resultsBody.innerHTML = '';

    if (!data.entries || data.entries.length === 0) {
        const noResults = document.getElementById('noResults');
        if (noResults) noResults.style.display = 'block';
        return;
    }

    const noResults = document.getElementById('noResults');
    if (noResults) noResults.style.display = 'none';

    data.entries.forEach(entry => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="timestamp">${entry.formattedTimestamp || formatTimestamp(entry.timestamp)}</td>
            <td><span class="log-level ${entry.level}">${entry.level}</span></td>
            <td>${escapeHtml(entry.sourceFile || '-')}</td>
            <td class="log-message" title="${escapeHtml(entry.message || '')}">${escapeHtml(entry.message || '')}</td>
        `;
        resultsBody.appendChild(row);
    });
}

async function compareSearch() {
    const keyword = document.getElementById('searchQuery')?.value.trim();
    if (!keyword) {
        showToast('Please enter a search query to compare', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/v1/compare?keyword=${encodeURIComponent(keyword)}`);
        if (!response.ok) throw new Error('Comparison failed');

        const data = await response.json();
        displayComparison(data);

    } catch (err) {
        console.error('Comparison error:', err);
        showToast('Comparison failed: ' + err.message, 'error');
    }
}

function displayComparison(data) {
    const container = document.getElementById('comparisonResult');
    if (!container) return;

    const agg = data.aggregations || {};
    container.innerHTML = `
        <div class="card">
            <div class="card-header"><h3>Search Method Comparison</h3></div>
            <div class="card-body">
                <div class="chart-bars">
                    <div class="chart-bar-item">
                        <span class="chart-label">Linear Search</span>
                        <div class="chart-bar-wrapper">
                            <div class="chart-bar naive" style="width: 100%"></div>
                        </div>
                        <span class="chart-value">${(agg.linearSearchTimeMs || 0).toFixed(2)} ms</span>
                    </div>
                    <div class="chart-bar-item">
                        <span class="chart-label">Indexed Search</span>
                        <div class="chart-bar-wrapper">
                            <div class="chart-bar optimized" style="width: ${((agg.indexedSearchTimeMs || 1) / (agg.linearSearchTimeMs || 1)) * 100}%"></div>
                        </div>
                        <span class="chart-value">${(agg.indexedSearchTimeMs || 0).toFixed(2)} ms</span>
                    </div>
                </div>
                <p style="margin-top: 1rem; text-align: center;">
                    <strong>Speedup: <span class="text-success">${agg.speedupFactor || 'N/A'}</span></strong>
                </p>
            </div>
        </div>
    `;
}

function clearSearch() {
    const queryEl = document.getElementById('searchQuery');
    const fromEl = document.getElementById('timeFrom');
    const toEl = document.getElementById('timeTo');

    if (queryEl) queryEl.value = '';
    if (fromEl) fromEl.value = '';
    if (toEl) toEl.value = '';

    const searchMeta = document.getElementById('searchMeta');
    if (searchMeta) searchMeta.style.display = 'none';

    const resultsCard = document.getElementById('resultsCard');
    if (resultsCard) resultsCard.style.display = 'none';
}

function exportResults() {
    if (lastSearchResults.length === 0) {
        showToast('No results to export', 'error');
        return;
    }

    let csv = 'Timestamp,Level,Source,Message\n';
    lastSearchResults.forEach(entry => {
        csv += `"${entry.formattedTimestamp || entry.timestamp}","${entry.level}","${entry.sourceFile || ''}","${(entry.message || '').replace(/"/g, '""')}"\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'loghawk-results.csv';
    a.click();
    URL.revokeObjectURL(url);
    showToast('Results exported as CSV', 'success');
}

// Utility functions
function formatTimestamp(ts) {
    if (!ts) return '-';
    return new Date(ts).toLocaleString('en-US', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
        hour12: false
    });
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
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

// Enter key triggers search
document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && document.activeElement?.id === 'searchQuery') {
        executeSearch();
    }
});