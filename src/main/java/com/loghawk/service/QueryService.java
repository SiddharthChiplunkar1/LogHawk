package com.loghawk.service;

import com.loghawk.coordinator.QueryCoordinator;
import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.model.LogEntry;
import com.loghawk.model.LogLevel;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QueryService {
    private static final Logger logger = LoggerFactory.getLogger(QueryService.class);

    private final QueryCoordinator coordinator;
    private final InvertedIndex invertedIndex;
    private final TimestampIndex timestampIndex;
    private final SearchEngine searchEngine;

    // Cache for frequently searched terms
    private final Map<String, QueryResult> searchCache;

    public QueryService(QueryCoordinator coordinator,
                        InvertedIndex invertedIndex,
                        TimestampIndex timestampIndex) {
        this.coordinator = coordinator;
        this.invertedIndex = invertedIndex;
        this.timestampIndex = timestampIndex;
        this.searchEngine = new SearchEngine();
        this.searchCache = new ConcurrentHashMap<>();
    }

    /**
     * Execute a query using the distributed coordinator
     */
    public QueryResult executeQuery(Query query) {
        logger.info("Executing query - keyword: {}, timeRange: {}",
                query.getKeyword(),
                query.hasTimeRange() ? "yes" : "no");

        long startTime = System.nanoTime();
        QueryResult result = coordinator.executeQuery(query);
        long duration = System.nanoTime() - startTime;

        logger.info("Query completed in {}ms, found {} matches across {} shards",
                duration / 1_000_000.0,
                result.getTotalMatches(),
                result.getShardsSearched());

        return result;
    }

    /**
     * Compare linear search vs indexed search performance
     */
    public QueryResult compareSearchMethods(String keyword) {
        logger.info("Comparing search methods for keyword: {}", keyword);

        Query query = new Query.Builder()
                .keyword(keyword)
                .maxResults(100)
                .build();

        // Get all entries for linear search
        List<LogEntry> allEntries = timestampIndex.getAllEntries();

        // Linear search timing
        long linearStart = System.nanoTime();
        QueryResult linearResult = searchEngine.linearSearch(query, allEntries);
        long linearTime = System.nanoTime() - linearStart;

        // Indexed search timing
        long indexedStart = System.nanoTime();
        QueryResult indexedResult = searchEngine.indexedSearch(
                query, invertedIndex, timestampIndex);
        long indexedTime = System.nanoTime() - indexedStart;

        // Add comparison data to indexed result
        indexedResult.addAggregation("linearSearchTimeMs", linearTime / 1_000_000.0);
        indexedResult.addAggregation("indexedSearchTimeMs", indexedTime / 1_000_000.0);

        if (linearTime > 0) {
            double speedup = (double) linearTime / indexedTime;
            indexedResult.addAggregation("speedupFactor", String.format("%.2fx", speedup));
        } else {
            indexedResult.addAggregation("speedupFactor", "N/A");
        }

        indexedResult.addAggregation("linearMatches", linearResult.getTotalMatches());
        indexedResult.addAggregation("indexedMatches", indexedResult.getTotalMatches());

        logger.info("Search comparison - Linear: {}ms, Indexed: {}ms, Speedup: {}",
                linearTime / 1_000_000.0,
                indexedTime / 1_000_000.0,
                linearTime > 0 ? String.format("%.2fx", (double) linearTime / indexedTime) : "N/A");

        return indexedResult;
    }

    /**
     * Get comprehensive system statistics
     */
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Basic stats
        stats.put("totalEntries", timestampIndex.size());
        stats.put("indexTerms", invertedIndex.getTotalTerms());
        stats.put("cacheSize", searchCache.size());

        // Coordinator stats
        Map<String, Object> coordinatorStats = coordinator.getStats();
        stats.put("coordinator", coordinatorStats);

        Map<String, Long> levelCounts = new LinkedHashMap<>();
        for (LogLevel level : LogLevel.values()) {
            levelCounts.put(level.getLabel(), 0L);
        }

        List<LogEntry> allEntries = timestampIndex.getAllEntries();
        for (LogEntry entry : allEntries) {
            String levelLabel = entry.getLevel().getLabel();  // Get String representation
            levelCounts.merge(levelLabel, 1L, Long::sum);     // Now key is String
        }
        stats.put("levelDistribution", levelCounts);

        // Time range info
        if (!allEntries.isEmpty()) {
            stats.put("oldestEntry", allEntries.get(0).getFormattedTimestamp());
            stats.put("newestEntry", allEntries.get(allEntries.size() - 1).getFormattedTimestamp());
        }

        // JVM stats
        Runtime runtime = Runtime.getRuntime();
        stats.put("jvmMemoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        stats.put("jvmMemoryTotalMB", runtime.totalMemory() / (1024 * 1024));
        stats.put("jvmMemoryMaxMB", runtime.maxMemory() / (1024 * 1024));
        stats.put("availableProcessors", runtime.availableProcessors());

        return stats;
    }

    /**
     * Get recent searches from cache
     */
    public Map<String, Object> getRecentSearches() {
        Map<String, Object> recent = new LinkedHashMap<>();
        recent.put("cachedSearches", searchCache.size());
        recent.put("searches", new ArrayList<>(searchCache.keySet()));
        return recent;
    }

    /**
     * Clear search cache
     */
    public void clearCache() {
        int size = searchCache.size();
        searchCache.clear();
        logger.info("Search cache cleared ({} entries removed)", size);
    }
}