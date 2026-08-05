// Auto-refresh stats every 10 seconds
let refreshInterval;

document.addEventListener('DOMContentLoaded', () => {
    loadStats();
    refreshInterval = setInterval(loadStats, 10000);
});

async function loadStats() {
    try {
        const response = await fetch('/api/v1/stats');
        if (!response.ok) throw new Error('Failed to fetch stats');

        const data = await response.json();
        updateStatCards(data);
        updateLogBreakdown(data);
        updateLevelDistribution(data);

    } catch (err) {
        console.error('Failed to load stats:', err);
    }
}

function updateStatCards(data) {
    // Update DOM elements if they exist
    setValue('totalEntries', data.totalEntries?.toLocaleString() || '0');
    setValue('indexTerms', data.indexTerms?.toLocaleString() || '0');

    if (data.coordinator) {
        setValue('totalShards', data.coordinator.totalShards || '0');
        setValue('activeThreads', data.coordinator.activeThreads || '0');
        setValue('completedTasks', data.coordinator.completedTasks?.toLocaleString() || '0');
    }
}

function updateLogBreakdown(data) {
    if (!data.levelDistribution) return;

    const dist = data.levelDistribution;
    const total = Object.values(dist).reduce((sum, val) => sum + val, 0) || 1;

    updateBar('errorBar', 'errorCount', dist.ERROR || 0, total);
    updateBar('warnBar', 'warnCount', dist.WARN || 0, total);
    updateBar('infoBar', 'infoCount', dist.INFO || 0, total);
}

function updateBar(barId, countId, count, total) {
    const bar = document.getElementById(barId);
    const countEl = document.getElementById(countId);
    if (bar) bar.style.width = ((count / total) * 100).toFixed(1) + '%';
    if (countEl) countEl.textContent = count.toLocaleString();
}

function updateLevelDistribution(data) {
    const container = document.getElementById('levelDistribution');
    if (!container || !data.levelDistribution) return;

    container.innerHTML = '';
    const dist = data.levelDistribution;

    for (const [level, count] of Object.entries(dist)) {
        const item = document.createElement('div');
        item.className = 'bar-item';
        item.innerHTML = `
            <span class="bar-label">${level}</span>
            <div class="bar">
                <div class="bar-fill ${level.toLowerCase()}" 
                     style="width: ${(count / Math.max(...Object.values(dist))) * 100}%"></div>
            </div>
            <span class="bar-count">${count.toLocaleString()}</span>
        `;
        container.appendChild(item);
    }
}

function setValue(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (refreshInterval) clearInterval(refreshInterval);
});