package com.loghawk.controller;

import com.loghawk.benchmark.BenchmarkRunner;
import com.loghawk.model.LogLevel;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.service.LogIngestionService;
import com.loghawk.service.QueryService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
public class LogHawkController {
    private static final Logger logger = LoggerFactory.getLogger(LogHawkController.class);

    private final QueryService queryService;
    private final LogIngestionService ingestionService;
    private final BenchmarkRunner benchmarkRunner;

    public LogHawkController(QueryService queryService,
                             LogIngestionService ingestionService,
                             BenchmarkRunner benchmarkRunner) {
        this.queryService = queryService;
        this.ingestionService = ingestionService;
        this.benchmarkRunner = benchmarkRunner;
    }


    @GetMapping("/")
    public String dashboard(Model model) {
        Map<String, Object> stats = queryService.getSystemStats();

        model.addAttribute("title", "LogHawk Dashboard");
        model.addAttribute("totalEntries", stats.getOrDefault("totalEntries", 0));
        model.addAttribute("indexTerms", stats.getOrDefault("indexTerms", 0));

        @SuppressWarnings("unchecked")
        Map<String, Object> coordinator = (Map<String, Object>) stats.getOrDefault("coordinator", Map.of());
        model.addAttribute("totalShards", coordinator.getOrDefault("totalShards", 0));
        model.addAttribute("activeThreads", coordinator.getOrDefault("activeThreads", 0));
        model.addAttribute("completedTasks", coordinator.getOrDefault("completedTasks", 0));

        return "dashboard";
    }

    @GetMapping("/search")
    public String searchPage(Model model) {
        model.addAttribute("title", "Search Logs");
        return "search";
    }

    @GetMapping("/benchmarks")
    public String benchmarksPage(Model model) {
        model.addAttribute("title", "Performance Benchmarks");
        return "benchmarks";
    }


    @GetMapping("/api/v1/health")
    @ResponseBody
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "LogHawk");
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }


    @GetMapping("/api/v1/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(queryService.getSystemStats());
    }


    @PostMapping("/api/v1/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        logger.info("Search request: keyword={}, timeRange=[{}, {}], levels={}",
                request.getKeyword(), request.getStartTime(), request.getEndTime(), request.getLevels());

        Query.Builder builder = new Query.Builder();

        if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
            builder.keyword(request.getKeyword());
        }

        if (request.getStartTime() != null && request.getEndTime() != null) {
            builder.timeRange(request.getStartTime(), request.getEndTime());
        }

        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            Set<LogLevel> levels = request.getLevels().stream()
                    .map(LogLevel::fromString)
                    .collect(Collectors.toSet());
            builder.levels(levels);
        }

        if (request.getMaxResults() != null && request.getMaxResults() > 0) {
            builder.maxResults(request.getMaxResults());
        }

        Query query = builder.build();
        QueryResult result = queryService.executeQuery(query);

        return ResponseEntity.ok(buildSearchResponse(result));
    }

    @GetMapping("/api/v1/compare")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compareSearch(@RequestParam String keyword) {
        logger.info("Search comparison requested for: {}", keyword);
        QueryResult result = queryService.compareSearchMethods(keyword);
        return ResponseEntity.ok(buildSearchResponse(result));
    }


    @GetMapping("/api/v1/benchmarks/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runBenchmarks() {
        logger.info("Running all benchmarks...");
        Map<String, Object> results = benchmarkRunner.runAllBenchmarks();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/api/v1/benchmarks/linear-vs-indexed")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> linearVsIndexed() {
        logger.info("Running linear vs indexed benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkLinearVsIndexed();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/api/v1/benchmarks/concurrency")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> concurrencyScaling() {
        logger.info("Running concurrency scaling benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkConcurrencyScaling();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/api/v1/benchmarks/time-range")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> timeRangeSearch() {
        logger.info("Running time-range search benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkTimeRangeSearch();
        return ResponseEntity.ok(results);
    }

    @PostMapping("/api/v1/ingestion/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "SIMPLE") String format) {

        logger.info("File upload: {} ({} bytes, format: {})",
                file.getOriginalFilename(), file.getSize(), format);

        try {
            LogIngestionService.IngestionResult result =
                    ingestionService.ingestMultipartFile(file, format);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("linesProcessed", result.getLinesProcessed());
            response.put("bytesProcessed", result.getBytesProcessed());
            response.put("durationSeconds", result.getDurationSeconds());
            response.put("throughputMBps", result.getThroughputMBps());

            logger.info("File ingested: {} lines in {:.2f}s ({:.2f} MB/s)",
                    result.getLinesProcessed(),
                    result.getDurationSeconds(),
                    result.getThroughputMBps());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to ingest file: {}", e.getMessage(), e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/api/v1/ingestion/upload-async")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "SIMPLE") String format) {

        logger.info("Async file upload: {}", file.getOriginalFilename());

        try {
            // Save to temp file first since async method takes a path
            java.io.File tempFile = java.io.File.createTempFile("loghawk_", ".log");
            file.transferTo(tempFile);

            CompletableFuture<LogIngestionService.IngestionResult> future =
                    ingestionService.ingestFileAsync(tempFile.getAbsolutePath(), format);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "File ingestion started asynchronously");
            response.put("fileName", file.getOriginalFilename());

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Failed to start async ingestion: {}", e.getMessage(), e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/api/v1/ingestion/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getIngestionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("isIngesting", ingestionService.isIngesting());
        return ResponseEntity.ok(status);
    }

    private Map<String, Object> buildSearchResponse(QueryResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("entries", result.getLogEntryList());
        response.put("queryTimeMs", result.getQueryTimeMillis());
        response.put("totalMatches", result.getTotalMatches());
        response.put("returnedMatches", result.getReturnedMatches());
        response.put("shardsSearched", result.getShardsSearched());
        response.put("aggregations", result.getAggregations());
        return response;
    }
}

/**
 * Search request DTO
 */
@Getter
@Setter
class SearchRequest {
    private String keyword;
    private Long startTime;
    private Long endTime;
    private List<String> levels;
    private Integer maxResults;

    @Override
    public String toString() {
        return String.format("SearchRequest[keyword=%s, timeRange=%s-%s, levels=%s, maxResults=%d]",
                keyword, startTime, endTime, levels, maxResults);
    }
}