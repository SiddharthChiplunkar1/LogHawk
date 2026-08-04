package com.loghawk.benchmark;

import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.model.LogEntry;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
public class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final SearchEngine searchEngine;
    private final InvertedIndex invertedIndex;
    private final TimestampIndex timestampIndex;

    public BenchmarkRunner(InvertedIndex invertedIndex, TimestampIndex timestampIndex) {
        this.searchEngine = new SearchEngine();
        this.invertedIndex = invertedIndex;
        this.timestampIndex = timestampIndex;
    }

    /**
     * Run all benchmarks
     */
    public Map<String, Object> runAllBenchmarks() {
        Map<String, Object> results = new LinkedHashMap<>();

        logger.info("Starting benchmarks...");

        results.put("linearVsIndexed", benchmarkLinearVsIndexed());
        results.put("concurrencyScaling", benchmarkConcurrencyScaling());
        results.put("timeRangeSearch", benchmarkTimeRangeSearch());
        results.put("systemInfo", getSystemInfo());

        logger.info("Benchmarks completed");

        return results;
    }

    /**
     * Compare linear search vs indexed search
     */
    public Map<String, Object> benchmarkLinearVsIndexed() {
        List<LogEntry> entries = timestampIndex.getAllEntries();

        if (entries.isEmpty()) {
            return Map.of("error", "No data available for benchmarking");
        }

        // Find common keywords in the data
        String[] testKeywords = findCommonKeywords(entries, 5);

        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> comparisons = new ArrayList<>();

        for (String keyword : testKeywords) {
            Query query = new Query.Builder()
                    .keyword(keyword)
                    .maxResults(1000)
                    .build();

            // Warm up
            searchEngine.indexedSearch(query, invertedIndex, timestampIndex);

            // Linear search (run 3 times, take average)
            long linearTime = 0;
            int linearMatches = 0;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                QueryResult result = searchEngine.linearSearch(query, entries);
                linearTime += (System.nanoTime() - start);
                linearMatches = result.getTotalMatches();
            }
            linearTime /= 3;

            // Indexed search (run 3 times, take average)
            long indexedTime = 0;
            int indexedMatches = 0;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                QueryResult result = searchEngine.indexedSearch(
                        query, invertedIndex, timestampIndex);
                indexedTime += (System.nanoTime() - start);
                indexedMatches = result.getTotalMatches();
            }
            indexedTime /= 3;

            Map<String, Object> comparison = new LinkedHashMap<>();
            comparison.put("keyword", keyword);
            comparison.put("linearTimeMs", linearTime / 1_000_000.0);
            comparison.put("indexedTimeMs", indexedTime / 1_000_000.0);
            comparison.put("speedup", String.format("%.2fx",
                    linearTime > 0 ? (double) linearTime / indexedTime : 0));
            comparison.put("matches", indexedMatches);

            comparisons.add(comparison);
        }

        results.put("comparisons", comparisons);
        results.put("totalEntries", entries.size());

        return results;
    }

    /**
     * Benchmark concurrency scaling
     */
    public Map<String, Object> benchmarkConcurrencyScaling() {
        Map<String, Object> results = new LinkedHashMap<>();
        int[] threadCounts = {1, 2, 4, 8, 16};
        List<Map<String, Object>> scalingData = new ArrayList<>();

        Query query = new Query.Builder()
                .keyword("test")
                .maxResults(100)
                .build();

        for (int threads : threadCounts) {
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            // Warm up
            runConcurrentSearch(executor, query, 10);

            // Actual test
            long startTime = System.nanoTime();
            runConcurrentSearch(executor, query, 100);
            long totalTime = System.nanoTime() - startTime;

            executor.shutdown();

            Map<String, Object> dataPoint = new LinkedHashMap<>();
            dataPoint.put("threads", threads);
            dataPoint.put("totalTimeMs", totalTime / 1_000_000.0);
            dataPoint.put("averageTimeMs", (totalTime / 100.0) / 1_000_000.0);
            dataPoint.put("throughput", 100.0 / (totalTime / 1_000_000_000.0));

            scalingData.add(dataPoint);
        }

        results.put("scalingData", scalingData);
        return results;
    }

    private void runConcurrentSearch(ExecutorService executor, Query query, int iterations) {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            futures.add(executor.submit(() -> {
                searchEngine.indexedSearch(query, invertedIndex, timestampIndex);
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore timeouts
            }
        }
    }

    /**
     * Benchmark time-range search performance
     */
    public Map<String, Object> benchmarkTimeRangeSearch() {
        List<LogEntry> entries = timestampIndex.getAllEntries();

        if (entries.size() < 100) {
            return Map.of("error", "Not enough data for time-range benchmarking");
        }

        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> rangeResults = new ArrayList<>();

        long newest = entries.get(entries.size() - 1).getTimestamp();
        long oldest = entries.get(0).getTimestamp();
        long totalRange = newest - oldest;

        // Test different range sizes
        double[] rangePercentages = {0.01, 0.05, 0.1, 0.25, 0.5, 1.0};

        for (double percentage : rangePercentages) {
            long rangeSize = (long) (totalRange * percentage);
            long startTime = newest - rangeSize;

            Query query = new Query.Builder()
                    .timeRange(startTime, newest)
                    .maxResults(10000)
                    .build();

            // Warm up
            searchEngine.indexedSearch(query, invertedIndex, timestampIndex);

            // Run 3 times, take average
            long totalTime = 0;
            int totalMatches = 0;
            for (int i = 0; i < 3; i++) {
                long start = System.nanoTime();
                QueryResult result = searchEngine.indexedSearch(
                        query, invertedIndex, timestampIndex);
                totalTime += (System.nanoTime() - start);
                totalMatches = result.getTotalMatches();
            }

            Map<String, Object> rangeData = new LinkedHashMap<>();
            rangeData.put("rangePercentage", String.format("%.1f%%", percentage * 100));
            rangeData.put("rangeHours", (rangeSize / 3600000.0));
            rangeData.put("averageTimeMs", (totalTime / 3.0) / 1_000_000.0);
            rangeData.put("matches", totalMatches);

            rangeResults.add(rangeData);
        }

        results.put("rangeResults", rangeResults);
        results.put("totalRangeHours", totalRange / 3600000.0);
        results.put("totalEntries", entries.size());

        return results;
    }

    /**
     * Find common keywords in the dataset
     */
    private String[] findCommonKeywords(List<LogEntry> entries, int count) {
        Map<String, Integer> wordFrequency = new HashMap<>();

        for (LogEntry entry : entries) {
            String[] words = entry.getMessage().toLowerCase()
                    .replaceAll("[^a-zA-Z0-9\\s]", " ")
                    .split("\\s+");

            for (String word : words) {
                if (word.length() > 3) {
                    wordFrequency.merge(word, 1, Integer::sum);
                }
            }
        }

        return wordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    /**
     * Get system information
     */
    private Map<String, Object> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();

        info.put("availableProcessors", runtime.availableProcessors());
        info.put("maxMemoryMB", runtime.maxMemory() / (1024 * 1024));
        info.put("totalMemoryMB", runtime.totalMemory() / (1024 * 1024));
        info.put("freeMemoryMB", runtime.freeMemory() / (1024 * 1024));
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));

        return info;
    }
}