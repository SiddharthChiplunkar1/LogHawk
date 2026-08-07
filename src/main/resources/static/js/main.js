// Utility: Format numbers with commas
const formatNumber = (num) => num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");

// --- DASHBOARD ---
async function fetchStats() {
    if (!document.getElementById('totalEntries')) return;
    
    try {
        const response = await fetch('/api/v1/stats');
        const data = await response.json();
        
        // Update basic stats
        document.getElementById('totalEntries').textContent = formatNumber(data.totalEntries || 0);
        document.getElementById('indexTerms').textContent = formatNumber(data.indexTerms || 0);
        
        if (data.coordinator) {
            document.getElementById('totalShards').textContent = data.coordinator.totalShards || 0;
            document.getElementById('activeThreads').textContent = data.coordinator.activeThreads || 0;
        }

        // System Info
        const infoList = document.getElementById('systemInfoList');
        if (infoList) {
            infoList.innerHTML = `
                <div class="detail-item"><span class="label">JVM Memory Used:</span> <span class="value">${data.jvmMemoryUsedMB || 0} MB</span></div>
                <div class="detail-item"><span class="label">JVM Memory Total:</span> <span class="value">${data.jvmMemoryTotalMB || 0} MB</span></div>
                <div class="detail-item"><span class="label">Available Processors:</span> <span class="value">${data.availableProcessors || 0}</span></div>
                <div class="detail-item"><span class="label">Oldest Log:</span> <span class="value">${data.oldestEntry || 'N/A'}</span></div>
                <div class="detail-item"><span class="label">Newest Log:</span> <span class="value">${data.newestEntry || 'N/A'}</span></div>
            `;
        }

        // Level Distribution
        const distContainer = document.getElementById('levelDistribution');
        if (distContainer && data.levelDistribution) {
            const total = Object.values(data.levelDistribution).reduce((a, b) => a + b, 0);
            let html = '';
            for (const [level, count] of Object.entries(data.levelDistribution)) {
                const percentage = total > 0 ? (count / total) * 100 : 0;
                let colorClass = 'var(--level-info)';
                if (level === 'WARN') colorClass = 'var(--level-warn)';
                if (level === 'ERROR') colorClass = 'var(--level-error)';
                if (level === 'DEBUG') colorClass = 'var(--level-debug)';
                
                html += `
                    <div class="dist-bar-wrapper">
                        <span class="dist-label">${level}</span>
                        <div class="dist-bar-container">
                            <div class="dist-bar" style="width: ${percentage}%; background-color: ${colorClass}"></div>
                        </div>
                        <span class="dist-value">${formatNumber(count)}</span>
                    </div>
                `;
            }
            distContainer.innerHTML = html;
        }
        
    } catch (err) {
        console.error('Failed to fetch stats', err);
    }
}


// --- SEARCH & AUTOCOMPLETE ---
let autocompleteTimeout = null;

function initSearchForm() {
    const form = document.getElementById('searchForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const keyword = document.getElementById('keyword').value;
        const startTimeStr = document.getElementById('startTime').value;
        const endTimeStr = document.getElementById('endTime').value;
        
        const levelCheckboxes = document.querySelectorAll('input[name="levels"]:checked');
        const levels = Array.from(levelCheckboxes).map(cb => cb.value);

        const requestBody = {
            keyword: keyword,
            levels: levels,
            maxResults: 100
        };

        if (startTimeStr) requestBody.startTime = new Date(startTimeStr).getTime();
        if (endTimeStr) requestBody.endTime = new Date(endTimeStr).getTime();

        try {
            document.getElementById('resultsSummary').textContent = 'Searching...';
            document.getElementById('resultsTable').querySelector('tbody').innerHTML = 
                `<tr><td colspan="4" style="text-align: center;"><div class="loader" style="margin:0 auto"></div></td></tr>`;
            
            // Note: will use /api/v1/search/boolean if the keyword has boolean operators later
            const endpoint = keyword.includes(' AND ') || keyword.includes(' OR ') || keyword.includes(' NOT ') 
                ? '/api/v1/search/boolean' : '/api/v1/search';
                
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });
            
            const data = await response.json();
            renderSearchResults(data);
        } catch (err) {
            console.error(err);
            document.getElementById('resultsSummary').textContent = 'Search failed.';
        }
    });

    const keywordInput = document.getElementById('keyword');
    const autocompleteDiv = document.getElementById('autocompleteSuggestions');
    
    keywordInput.addEventListener('input', (e) => {
        clearTimeout(autocompleteTimeout);
        const prefix = e.target.value;
        
        if (prefix.length < 2) {
            autocompleteDiv.style.display = 'none';
            return;
        }
        
        autocompleteTimeout = setTimeout(async () => {
            try {
                // Split by spaces to only autocomplete the current word
                const words = prefix.split(/\s+/);
                const lastWord = words[words.length - 1];
                
                if (lastWord.length < 2) {
                    autocompleteDiv.style.display = 'none';
                    return;
                }

                const res = await fetch(`/api/v1/suggest?prefix=${encodeURIComponent(lastWord)}`);
                if(res.ok) {
                    const suggestions = await res.json();
                    if (suggestions.length > 0) {
                        autocompleteDiv.innerHTML = suggestions.map(s => 
                            `<div class="suggestion-item">${s}</div>`
                        ).join('');
                        autocompleteDiv.style.display = 'block';
                        
                        document.querySelectorAll('.suggestion-item').forEach(item => {
                            item.addEventListener('click', () => {
                                words[words.length - 1] = item.textContent;
                                keywordInput.value = words.join(' ') + ' ';
                                autocompleteDiv.style.display = 'none';
                                keywordInput.focus();
                            });
                        });
                    } else {
                        autocompleteDiv.style.display = 'none';
                    }
                }
            } catch (err) {
                // Ignore
            }
        }, 300);
    });

    document.addEventListener('click', (e) => {
        if (!e.target.closest('.search-input-group')) {
            autocompleteDiv.style.display = 'none';
        }
    });
}

function renderSearchResults(data) {
    const summary = document.getElementById('resultsSummary');
    const queryTime = document.getElementById('queryTime');
    const tbody = document.getElementById('resultsTable').querySelector('tbody');
    
    summary.textContent = `Found ${formatNumber(data.totalMatches || 0)} matches`;
    queryTime.textContent = `${data.queryTimeMs ? data.queryTimeMs.toFixed(2) : 0} ms | ${data.shardsSearched || 0} shards`;
    
    if (!data.entries || data.entries.length === 0) {
        tbody.innerHTML = `<tr class="empty-state"><td colspan="4">No results found</td></tr>`;
        return;
    }
    
    tbody.innerHTML = data.entries.map(entry => {
        // Simple regex to format date array back to string if needed, 
        // but assuming the backend returns a formatted string or timestamp.
        let timestamp = entry.formattedTimestamp || entry.timestamp;
        
        return `
        <tr>
            <td style="color: var(--text-secondary); white-space: nowrap;">${timestamp}</td>
            <td><span class="badge ${entry.level}">${entry.level}</span></td>
            <td style="color: var(--accent-secondary);">${entry.thread}</td>
            <td style="word-break: break-all;">${escapeHtml(entry.message)}</td>
        </tr>
    `}).join('');
}

// --- BENCHMARKS ---
function initBenchmarks() {
    const panel = document.getElementById('resultsPanel');
    const loader = document.getElementById('benchmarkLoader');
    const output = document.getElementById('benchmarkOutput');
    
    const run = async (endpoint) => {
        panel.style.display = 'block';
        loader.style.display = 'block';
        output.textContent = 'Running benchmark... this may take a few seconds.';
        
        try {
            const res = await fetch(endpoint);
            const data = await res.json();
            output.textContent = JSON.stringify(data, null, 2);
        } catch (err) {
            output.textContent = 'Benchmark failed: ' + err.message;
        } finally {
            loader.style.display = 'none';
        }
    };
    
    document.getElementById('btnRunAll')?.addEventListener('click', () => run('/api/v1/benchmarks/run'));
    document.getElementById('btnRunLinear')?.addEventListener('click', () => run('/api/v1/benchmarks/linear-vs-indexed'));
    document.getElementById('btnRunConcurrency')?.addEventListener('click', () => run('/api/v1/benchmarks/concurrency'));
    document.getElementById('btnRunTimeRange')?.addEventListener('click', () => run('/api/v1/benchmarks/time-range'));
}

// --- UPLOAD ---
function initUpload() {
    const form = document.getElementById('uploadForm');
    if (!form) return;

    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('fileInput');
    const fileInfo = document.getElementById('fileInfo');
    const fileName = document.getElementById('fileName');
    const removeFile = document.getElementById('removeFile');
    const btnUpload = document.getElementById('btnUpload');
    
    // Drag and drop events
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, preventDefaults, false);
    });
    
    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }
    
    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.add('dragover'), false);
    });
    
    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, () => dropZone.classList.remove('dragover'), false);
    });
    
    dropZone.addEventListener('drop', (e) => {
        let dt = e.dataTransfer;
        let files = dt.files;
        handleFiles(files);
    });
    
    fileInput.addEventListener('change', function() {
        handleFiles(this.files);
    });
    
    removeFile.addEventListener('click', () => {
        fileInput.value = '';
        fileInfo.style.display = 'none';
        dropZone.style.display = 'flex';
        btnUpload.disabled = true;
    });
    
    function handleFiles(files) {
        if (files.length > 0) {
            const file = files[0];
            fileInput.files = files; // Assign to input
            fileName.textContent = file.name + ` (${(file.size / 1024 / 1024).toFixed(2)} MB)`;
            dropZone.style.display = 'none';
            fileInfo.style.display = 'flex';
            btnUpload.disabled = false;
        }
    }
    
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (fileInput.files.length === 0) return;
        
        const file = fileInput.files[0];
        const format = document.getElementById('logFormat').value;
        const formData = new FormData();
        formData.append('file', file);
        formData.append('format', format);
        
        const progressContainer = document.getElementById('progressContainer');
        const progressBar = document.getElementById('progressBar');
        const progressPercentage = document.getElementById('progressPercentage');
        const resultDiv = document.getElementById('uploadResult');
        
        progressContainer.style.display = 'block';
        resultDiv.style.display = 'none';
        btnUpload.disabled = true;
        
        try {
            // Simulated progress since fetch doesn't support upload progress natively easily without XHR
            let progress = 0;
            const intv = setInterval(() => {
                progress += 5;
                if(progress > 90) clearInterval(intv);
                progressBar.style.width = progress + '%';
                progressPercentage.textContent = progress + '%';
            }, 200);

            const response = await fetch('/api/v1/ingestion/upload', {
                method: 'POST',
                body: formData
            });
            
            clearInterval(intv);
            progressBar.style.width = '100%';
            progressPercentage.textContent = '100%';
            
            const data = await response.json();
            
            resultDiv.style.display = 'block';
            if (response.ok) {
                resultDiv.innerHTML = `
                    <div style="color: var(--level-debug); margin-bottom: 0.5rem;"><i class="ph ph-check-circle"></i> Upload and Ingestion Complete</div>
                    <div class="code-block" style="background: rgba(0,0,0,0.2); padding: 1rem;">
                        Lines Processed: ${formatNumber(data.linesProcessed)}<br>
                        Duration: ${data.durationSeconds.toFixed(2)}s<br>
                        Throughput: ${data.throughputMBps.toFixed(2)} MB/s
                    </div>
                `;
            } else {
                resultDiv.innerHTML = `<div style="color: var(--level-error);"><i class="ph ph-warning"></i> Error: ${data.error || 'Upload failed'}</div>`;
            }
        } catch (err) {
            resultDiv.style.display = 'block';
            resultDiv.innerHTML = `<div style="color: var(--level-error);"><i class="ph ph-warning"></i> Request failed: ${err.message}</div>`;
        } finally {
            btnUpload.disabled = false;
        }
    });
}

// --- LIVE FEED (WebSocket Placeholder) ---
let ws = null;
function initLiveFeed() {
    const btnConnect = document.getElementById('btnConnect');
    const btnDisconnect = document.getElementById('btnDisconnect');
    const btnClear = document.getElementById('btnClearFeed');
    const feed = document.getElementById('liveFeed');
    const wsStatus = document.getElementById('wsStatus');
    
    if(!btnConnect) return;

    btnConnect.addEventListener('click', () => {
        // Connect WebSocket
        ws = new WebSocket(`ws://${window.location.host}/api/v1/stream`);
        
        ws.onopen = () => {
            btnConnect.disabled = true;
            btnDisconnect.disabled = false;
            wsStatus.querySelector('.status-dot').classList.add('online');
            wsStatus.querySelector('.status-text').textContent = 'Connected';
            feed.innerHTML = '';
            appendLog({ level: 'INFO', timestamp: new Date().toISOString(), message: 'Connected to Live Feed...' });
        };
        
        ws.onmessage = (event) => {
            try {
                const entry = JSON.parse(event.data);
                
                // Filtering
                const filterId = `liveFilter${entry.level.charAt(0) + entry.level.slice(1).toLowerCase()}`;
                const checkbox = document.getElementById(filterId);
                if (checkbox && !checkbox.checked) return;
                
                appendLog(entry);
            } catch (e) {
                console.error("Parse error", e);
            }
        };
        
        ws.onclose = () => {
            btnConnect.disabled = false;
            btnDisconnect.disabled = true;
            wsStatus.querySelector('.status-dot').classList.remove('online');
            wsStatus.querySelector('.status-text').textContent = 'Disconnected';
            appendLog({ level: 'WARN', timestamp: new Date().toISOString(), message: 'Disconnected from Live Feed.' });
        };
    });

    btnDisconnect.addEventListener('click', () => {
        if (ws) ws.close();
    });

    btnClear.addEventListener('click', () => {
        feed.innerHTML = '';
    });
    
    function appendLog(entry) {
        const div = document.createElement('div');
        div.className = `live-log-line ${entry.level.toLowerCase()}`;
        
        let ts = entry.formattedTimestamp || entry.timestamp || '';
        
        div.innerHTML = `
            <span class="live-ts">[${ts}]</span>
            <span class="badge ${entry.level}">${entry.level}</span>
            <span class="live-msg">${escapeHtml(entry.message)}</span>
        `;
        
        feed.appendChild(div);
        
        // Auto-scroll
        if (feed.children.length > 1000) {
            feed.removeChild(feed.firstChild);
        }
        feed.scrollTop = feed.scrollHeight;
    }
}

function escapeHtml(unsafe) {
    if(!unsafe) return '';
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}
