package com.loghawk.controller;

import com.loghawk.benchmark.BenchmarkRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/benchmarks")
@CrossOrigin(origins = "*")
public class BenchmarkController {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);

    private final BenchmarkRunner benchmarkRunner;

    public BenchmarkController(BenchmarkRunner benchmarkRunner) {
        this.benchmarkRunner = benchmarkRunner;
    }

    @GetMapping("/run")
    public ResponseEntity<Map<String, Object>> runBenchmarks() {
        logger.info("Running all benchmarks...");
        Map<String, Object> results = benchmarkRunner.runAllBenchmarks();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/linear-vs-indexed")
    public ResponseEntity<Map<String, Object>> linearVsIndexed() {
        logger.info("Running linear vs indexed benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkLinearVsIndexed();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/concurrency")
    public ResponseEntity<Map<String, Object>> concurrencyScaling() {
        logger.info("Running concurrency scaling benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkConcurrencyScaling();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/time-range")
    public ResponseEntity<Map<String, Object>> timeRangeSearch() {
        logger.info("Running time-range search benchmark...");
        Map<String, Object> results = benchmarkRunner.benchmarkTimeRangeSearch();
        return ResponseEntity.ok(results);
    }
}