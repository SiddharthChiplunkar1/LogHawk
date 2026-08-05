package com.loghawk.util;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class LogDataGenerator {
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "INFO",
            "WARN", "WARN", "ERROR", "DEBUG"};
    private static final String[] THREADS = {"main", "http-nio-8080-exec-1",
            "http-nio-8080-exec-2", "pool-1-thread-1",
            "scheduler-1"};

    private static final String[] INFO_MESSAGES = {
            "User login successful for userId={}",
            "Request processed in {}ms",
            "Connection established to database",
            "Cache refreshed successfully",
            "Health check passed",
            "Configuration reloaded",
            "File upload complete: {} bytes",
            "Session created for user {}"
    };

    private static final String[] WARN_MESSAGES = {
            "Connection pool running low: {} connections remaining",
            "Request took longer than expected: {}ms",
            "Memory usage above threshold: {}%",
            "Retry attempt {} for service call",
            "Deprecated API called: {}",
            "Disk usage at {}%"
    };

    private static final String[] ERROR_MESSAGES = {
            "OutOfMemoryError: Java heap space",
            "NullPointerException at com.app.Service.process()",
            "Failed to connect to database: connection timeout",
            "FileNotFoundException: config/application.yml",
            "StackOverflowError in recursive method",
            "IllegalArgumentException: Invalid parameter value",
            "SQLException: Connection refused",
            "TimeoutException: Operation timed out after 30000ms"
    };

    private static final String[] DEBUG_MESSAGES = {
            "Entering method: processRequest",
            "Variable value: counter={}",
            "Query executed: SELECT * FROM users WHERE id={}",
            "Cache hit for key: {}",
            "Thread state: RUNNABLE"
    };

    /**
     * Generate a sample log file with realistic entries.
     */
    public static File generateSampleFile(String filePath, int totalLines, int daysBack)
            throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        long now = System.currentTimeMillis();
        long startTime = now - (daysBack * 24L * 3600_000L);
        long timeRange = now - startTime;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (int i = 0; i < totalLines; i++) {
                String line = generateLogLine(startTime, timeRange, i);
                writer.write(line);
                writer.newLine();

                if (i % 100000 == 0 && i > 0) {
                    System.out.printf("Generated %,d lines...\n", i);
                }
            }
        }

        System.out.printf("Generated %s with %,d lines (%.2f MB)\n",
                filePath, totalLines, file.length() / 1_000_000.0);
        return file;
    }

    private static String generateLogLine(long startTime, long timeRange, int lineNum) {
        long timestamp = startTime + (long)(RANDOM.nextDouble() * timeRange);
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        String level = LEVELS[RANDOM.nextInt(LEVELS.length)];
        String thread = THREADS[RANDOM.nextInt(THREADS.length)];
        String message = generateMessage(level, lineNum);

        return String.format("[%s] %s [%s] %s",
                dateTime.format(FORMATTER), level, thread, message);
    }

    private static String generateMessage(String level, int lineNum) {
        String template = switch (level) {
            case "WARN" -> WARN_MESSAGES[RANDOM.nextInt(WARN_MESSAGES.length)];
            case "ERROR" -> ERROR_MESSAGES[RANDOM.nextInt(ERROR_MESSAGES.length)];
            case "DEBUG" -> DEBUG_MESSAGES[RANDOM.nextInt(DEBUG_MESSAGES.length)];
            default -> INFO_MESSAGES[RANDOM.nextInt(INFO_MESSAGES.length)];
        };

        // Replace placeholders with random values
        String result = template;
        while (result.contains("{}")) {
            result = result.replaceFirst("\\{\\}", String.valueOf(RANDOM.nextInt(1000)));
        }
        return result;
    }

    // Run standalone to generate sample files
    public static void main(String[] args) throws IOException {
        // Generate 500K lines spanning 7 days
        generateSampleFile("sample-logs/application.log", 500_000, 7);

        // Generate a smaller one for quick testing
        generateSampleFile("sample-logs/test.log", 10_000, 1);
    }
}