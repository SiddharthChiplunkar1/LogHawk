package com.loghawk.coordinator;

import com.loghawk.model.LogEntry;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.shard.LogShard;
import com.loghawk.shard.ShardNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class QueryCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(QueryCoordinator.class);

    private final List<LogShard> shards;
    private final ExecutorService executorService;

    public QueryCoordinator(List<LogShard> shards) {
        this.shards = shards;
        // Create thread pool with size based on number of shards
        this.executorService = Executors.newFixedThreadPool(
                Math.min(shards.size(), Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("Coordinator-Worker-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );
        logger.info("QueryCoordinator initialized with {} shards and {} worker threads",
                shards.size(),
                Math.min(shards.size(), Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Execute a query across all relevant shards in parallel
     */
    public QueryResult executeQuery(Query query) {
        long startTime = System.nanoTime();

        // Step 1: Find which shards are relevant to this query
        List<LogShard> relevantShards = getRelevantShards(query);
        logger.debug("Query will search {} out of {} shards", relevantShards.size(), shards.size());

        if (relevantShards.isEmpty()) {
            logger.warn("No relevant shards found for query");
            return new QueryResult(Collections.emptyList(),
                    System.nanoTime() - startTime, 0, 0);
        }

        // Step 2: Fire parallel queries to all relevant shards
        List<CompletableFuture<QueryResult>> futures = new ArrayList<>();

        for (LogShard shard : relevantShards) {
            CompletableFuture<QueryResult> future = CompletableFuture.supplyAsync(() -> {
                logger.debug("Searching shard: {}", shard.getShardId());
                ShardNode node = new ShardNode(shard, query);
                return node.call();
            }, executorService);

            futures.add(future);
        }

        // Step 3: Wait for all shards to complete
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            // Set timeout to prevent infinite waiting
            allFutures.get(30, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            logger.error("Query timeout after 30 seconds");
            // Cancel remaining futures
            futures.forEach(f -> f.cancel(true));
            return new QueryResult(Collections.emptyList(),
                    System.nanoTime() - startTime, 0, relevantShards.size());
        } catch (Exception e) {
            logger.error("Error during parallel query execution: {}", e.getMessage(), e);
            return new QueryResult(Collections.emptyList(),
                    System.nanoTime() - startTime, 0, relevantShards.size());
        }

        // Step 4: Collect results from all shards
        List<QueryResult> partialResults = futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        logger.error("Failed to get result from shard: {}", e.getMessage());
                        return new QueryResult(Collections.emptyList(), 0, 0, 0);
                    }
                })
                .collect(Collectors.toList());

        // Step 5: Merge all partial results
        QueryResult mergedResult = mergeResults(partialResults, startTime, relevantShards.size());

        logger.info("Query completed: {} results from {} shards in {}ms",
                mergedResult.getReturnedMatches(),
                relevantShards.size(),
                String.format("%.2f", mergedResult.getQueryTimeMillis()));

        return mergedResult;
    }

    /**
     * Determine which shards are relevant based on the query's time range
     */
    private List<LogShard> getRelevantShards(Query query) {
        // If no time range specified, search all shards
        if (!query.hasTimeRange()) {
            logger.debug("No time range specified, searching all {} shards", shards.size());
            return new ArrayList<>(shards);
        }

        long queryStart = query.getTimeRangeStart();
        long queryEnd = query.getTimeRangeEnd();

        // Find shards that overlap with the query's time range
        List<LogShard> relevant = shards.stream()
                .filter(shard -> {
                    long shardStart = shard.getStartTimeRange();
                    long shardEnd = shard.getEndTimeRange();

                    // Check for time range overlap
                    boolean overlaps = shardStart <= queryEnd && shardEnd >= queryStart;

                    if (overlaps) {
                        logger.debug("Shard {} overlaps with query time range", shard.getShardId());
                    }

                    return overlaps;
                })
                .collect(Collectors.toList());

        logger.debug("Found {} relevant shards for time range query", relevant.size());
        return relevant;
    }

    /**
     * Merge results from multiple shards into a single QueryResult
     */
    private QueryResult mergeResults(List<QueryResult> partialResults,
                                     long startTime, int shardsSearched) {
        List<LogEntry> allEntries = new ArrayList<>();
        int totalMatches = 0;
        long totalShardTime = 0;

        // Collect entries and statistics from all shards
        for (QueryResult result : partialResults) {
            allEntries.addAll(result.getLogEntryList());
            totalMatches += result.getTotalMatches();
            totalShardTime += result.getQueryTimeNanos();
        }

        // Sort merged entries by timestamp (most recent first for typical use cases)
        allEntries.sort((e1, e2) -> {
            // Sort by timestamp descending (newest first)
            int timeCompare = Long.compare(e2.getTimestamp(), e1.getTimestamp());
            if (timeCompare != 0) return timeCompare;
            // If same timestamp, sort by ID
            return Long.compare(e2.getId(), e1.getId());
        });

        long endTime = System.nanoTime();
        QueryResult mergedResult = new QueryResult(
                allEntries,
                endTime - startTime,
                totalMatches,
                shardsSearched
        );

        // Add aggregation data
        mergedResult.addAggregation("shardsSearched", shardsSearched);
        mergedResult.addAggregation("totalShardTimeMs", totalShardTime / 1_000_000.0);
        mergedResult.addAggregation("mergeTimeMs", (endTime - startTime - totalShardTime) / 1_000_000.0);
        mergedResult.addAggregation("averageShardTimeMs",
                shardsSearched > 0 ? (totalShardTime / 1_000_000.0) / shardsSearched : 0);

        return mergedResult;
    }

    /**
     * Execute a linear search across all shards for comparison purposes
     */
    public QueryResult executeLinearSearch(Query query) {
        long startTime = System.nanoTime();
        List<LogEntry> allResults = new ArrayList<>();
        int shardsSearched = 0;

        // Get all entries from all shards
        for (LogShard shard : shards) {
            if (query.hasTimeRange()) {
                if (shard.getStartTimeRange() <= query.getTimeRangeEnd() &&
                        shard.getEndTimeRange() >= query.getTimeRangeStart()) {
                    allResults.addAll(shard.getTimestampIndex().getAllEntries());
                    shardsSearched++;
                }
            } else {
                allResults.addAll(shard.getTimestampIndex().getAllEntries());
                shardsSearched++;
            }
        }

        // Manual filtering (simulating linear search)
        List<LogEntry> filteredResults = new ArrayList<>();
        for (LogEntry entry : allResults) {
            boolean matches = true;

            // Keyword filter
            if (query.hasKeyword() && !entry.getMessage().toLowerCase()
                    .contains(query.getKeyword().toLowerCase())) {
                matches = false;
            }

            // Level filter
            if (!query.getRequiredLevels().isEmpty() &&
                    !query.getRequiredLevels().contains(entry.getLevel())) {
                matches = false;
            }

            if (matches) {
                filteredResults.add(entry);
                if (filteredResults.size() >= query.getMaxResults()) {
                    break;
                }
            }
        }

        long endTime = System.nanoTime();
        QueryResult result = new QueryResult(
                filteredResults,
                endTime - startTime,
                filteredResults.size(),
                shardsSearched
        );
        result.addAggregation("searchMethod", "LINEAR");

        return result;
    }

    /**
     * Get statistics about the coordinator
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalShards", shards.size());

        // Shard details
        List<Map<String, Object>> shardDetails = new ArrayList<>();
        for (LogShard shard : shards) {
            Map<String, Object> shardInfo = new HashMap<>();
            shardInfo.put("id", shard.getShardId());
            shardInfo.put("startTime", shard.getStartTimeRange());
            shardInfo.put("endTime", shard.getEndTimeRange());
            shardInfo.put("entries", shard.getTimestampIndex().size());
            shardInfo.put("indexTerms", shard.getInvertedIndex().getTotalTerms());
            shardDetails.add(shardInfo);
        }
        stats.put("shardDetails", shardDetails);

        // Thread pool stats
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            stats.put("activeThreads", tpe.getActiveCount());
            stats.put("poolSize", tpe.getPoolSize());
            stats.put("corePoolSize", tpe.getCorePoolSize());
            stats.put("maxPoolSize", tpe.getMaximumPoolSize());
            stats.put("completedTasks", tpe.getCompletedTaskCount());
            stats.put("queueSize", tpe.getQueue().size());
        }

        return stats;
    }


    public void shutdown() {
        logger.info("Shutting down QueryCoordinator...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Forcing shutdown after timeout");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("QueryCoordinator shutdown complete");
    }

    @javax.annotation.PreDestroy
    public void preDestroy() {
        shutdown();
    }
}