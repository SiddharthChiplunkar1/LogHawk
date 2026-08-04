package com.loghawk.controller;

import com.loghawk.model.LogLevel;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;
import com.loghawk.service.QueryService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class QueryController {
    private static final Logger logger = LoggerFactory.getLogger(QueryController.class);

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Execute a search query
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        logger.info("Search request received: {}", request);

        Query.Builder builder = new Query.Builder();

        // Keyword
        if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
            builder.keyword(request.getKeyword());
        }

        // Time range
        if (request.getStartTime() != null && request.getEndTime() != null) {
            builder.timeRange(request.getStartTime(), request.getEndTime());
        }

        // Log levels
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            Set<LogLevel> levels = request.getLevels().stream()
                    .map(LogLevel::fromString)
                    .collect(Collectors.toSet());
            builder.levels(levels);
        }

        // Max results
        if (request.getMaxResults() != null && request.getMaxResults() > 0) {
            builder.maxResults(request.getMaxResults());
        }

        Query query = builder.build();
        QueryResult result = queryService.executeQuery(query);

        return ResponseEntity.ok(buildResponse(result));
    }

    /**
     * Compare linear vs indexed search
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareSearch(@RequestParam String keyword) {
        logger.info("Search comparison requested for: {}", keyword);
        QueryResult result = queryService.compareSearchMethods(keyword);
        return ResponseEntity.ok(buildResponse(result));
    }

    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(queryService.getSystemStats());
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "LogHawk");
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }

    /**
     * Build standardized response
     */
    private Map<String, Object> buildResponse(QueryResult result) {
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
        return String.format("SearchRequest[keyword=%s," +
                        "timeRange=%s-%s, levels=%s, maxResults=%d]",
                keyword, startTime, endTime, levels, maxResults);
    }
}