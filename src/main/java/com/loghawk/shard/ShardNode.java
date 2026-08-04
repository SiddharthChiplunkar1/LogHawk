package com.loghawk.shard;

import com.loghawk.model.LogEntry;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Callable;

public class ShardNode implements Callable<QueryResult> {
    private static final Logger logger = LoggerFactory.getLogger(ShardNode.class);

    private final LogShard shard;
    private final SearchEngine searchEngine;
    private final Query query;

    public ShardNode(LogShard shard, Query query) {
        this.shard = shard;
        this.searchEngine = new SearchEngine();
        this.query = query;
    }

    @Override
    public QueryResult call() {
        logger.debug("Shard {} starting search", shard.getShardId());
        long startTime = System.nanoTime();

        try {
            // Check if this shard has any data
            if (shard.getTimestampIndex().size() == 0) {
                logger.debug("Shard {} is empty, skipping", shard.getShardId());
                return new QueryResult(Collections.emptyList(),
                        System.nanoTime() - startTime, 0, 1);
            }

            // Perform indexed search on this shard
            QueryResult result = searchEngine.indexedSearch(
                    query,
                    shard.getInvertedIndex(),
                    shard.getTimestampIndex()
            );

            // Add shard-specific metadata
            result.addAggregation("shardId", shard.getShardId());
            result.addAggregation("shardEntries", shard.getTimestampIndex().size());

            long duration = System.nanoTime() - startTime;
            logger.debug("Shard {} completed search in {}ms, found {} matches",
                    shard.getShardId(),
                    duration / 1_000_000.0,
                    result.getTotalMatches());

            return result;

        } catch (Exception e) {
            logger.error("Error searching shard {}: {}", shard.getShardId(), e.getMessage());
            return new QueryResult(Collections.emptyList(),
                    System.nanoTime() - startTime, 0, 1);
        }
    }

    /**
     * Perform linear search on this shard for comparison
     */
    public QueryResult linearSearch() {
        long startTime = System.nanoTime();

        try {
            List<LogEntry> allEntries = shard.getTimestampIndex().getAllEntries();
            QueryResult result = searchEngine.linearSearch(query, allEntries);

            result.addAggregation("shardId", shard.getShardId());
            result.addAggregation("searchMethod", "LINEAR");

            return result;

        } catch (Exception e) {
            logger.error("Error in linear search on shard {}: {}",
                    shard.getShardId(), e.getMessage());
            return new QueryResult(Collections.emptyList(),
                    System.nanoTime() - startTime, 0, 1);
        }
    }

    public String getShardId() {
        return shard.getShardId();
    }

    @Override
    public String toString() {
        return String.format("ShardNode[%s]", shard.getShardId());
    }
}