package com.loghawk.controller;

import com.loghawk.service.LogIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ingestion")
@CrossOrigin(origins = "*")
public class IngestionController {
    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final LogIngestionService ingestionService;

    public IngestionController(LogIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Upload and ingest a log file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "SIMPLE") String format) {

        logger.info("File upload received: {} ({} bytes, format: {})",
                file.getOriginalFilename(), file.getSize(), format);

        try {
            LogIngestionService.IngestionResult result =
                    ingestionService.ingestMultipartFile(file, format);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("linesProcessed", result.getLinesProcessed());
            response.put("bytesProcessed", result.getBytesProcessed());
            response.put("durationSeconds", result.getDurationSeconds());
            response.put("throughputMBps", result.getThroughputMBps());

            logger.info("File ingested: {} lines in {}s ({} MB/s)",
                    result.getLinesProcessed(),
                    String.format("%.2f", result.getDurationSeconds()),
                    String.format("%.2f", result.getThroughputMBps()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to ingest file: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Upload file asynchronously
     */
    @PostMapping("/upload-async")
    public ResponseEntity<Map<String, Object>> uploadFileAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "SIMPLE") String format) {

        logger.info("Async file upload received: {}", file.getOriginalFilename());

        try {
            CompletableFuture<LogIngestionService.IngestionResult> future =
                    ingestionService.ingestFileAsync(file.getOriginalFilename(), format);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File ingestion started asynchronously");
            response.put("fileName", file.getOriginalFilename());

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Failed to start async ingestion: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get ingestion status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isIngesting", ingestionService.isIngesting());
        return ResponseEntity.ok(status);
    }
}