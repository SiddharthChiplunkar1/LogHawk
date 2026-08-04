package com.loghawk.search;

import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.model.LogEntry;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;

import java.util.*;

public class SearchEngine {

    public QueryResult linearSearch(Query query, List<LogEntry> entries) {
        long startTime = System.nanoTime();
        List<LogEntry> results = new ArrayList<>();

        for (LogEntry entry : entries) {
            if (matchesEntry(entry, query)) {
                results.add(entry);
                if (results.size() >= query.getMaxResults()) break;
            }
        }

        return new QueryResult(results, System.nanoTime() - startTime, results.size(), 1);
    }

    public QueryResult indexedSearch(Query query, InvertedIndex invertedIndex,
                                     TimestampIndex timestampIndex) {
        long startTime = System.nanoTime();
        Set<Long> candidateIds = null;

        // Keyword filtering via inverted index
        if (query.hasKeyword()) {
            List<String> keywords = Arrays.asList(query.getKeyword().split("\\s+"));
            candidateIds = invertedIndex.searchByKeywords(keywords);
            if (candidateIds.isEmpty()) {
                return new QueryResult(Collections.emptyList(),
                        System.nanoTime() - startTime, 0, 1);
            }
        }

        // Time range filtering via binary search
        List<LogEntry> timeFiltered;
        if (query.hasTimeRange()) {
            timeFiltered = timestampIndex.searchByTimeRange(
                    query.getTimeRangeStart(), query.getTimeRangeEnd());
        } else {
            timeFiltered = timestampIndex.getAllEntries();
        }

        // Combine filters
        List<LogEntry> results = new ArrayList<>();
        for (LogEntry entry : timeFiltered) {
            if (candidateIds == null || candidateIds.contains(entry.getId())) {
                if (matchesEntry(entry, query)) {
                    results.add(entry);
                    if (results.size() >= query.getMaxResults()) break;
                }
            }
        }

        return new QueryResult(results, System.nanoTime() - startTime, results.size(), 1);
    }

    private boolean matchesEntry(LogEntry entry, Query query) {
        // Level filter
        if (!query.getRequiredLevels().isEmpty() &&
                !query.getRequiredLevels().contains(entry.getLevel())) {
            return false;
        }
        return true;
    }
}