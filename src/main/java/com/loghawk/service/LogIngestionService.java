package com.loghawk.service;

import com.loghawk.index.IndexBuilder;
import com.loghawk.ingestion.LogChunk;
import com.loghawk.model.LogEntry;
import com.loghawk.parser.LogParser;
import com.loghawk.parser.ParserFactory;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@Service
public class LogIngestionService {
    private static final Logger logger = LoggerFactory.getLogger(LogIngestionService.class);

    private final IndexBuilder indexBuilder;
    private final ExecutorService consumerPool;
    private final BlockingQueue<LogChunk> chunkQueue;
    private final AtomicLong totalLinesProcessed;
    private final AtomicLong totalBytesProcessed;
    private volatile boolean isIngesting;

    public LogIngestionService(IndexBuilder indexBuilder) {
        this.indexBuilder = indexBuilder;
        this.consumerPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
        this.chunkQueue = new LinkedBlockingQueue<>(200);
        this.totalLinesProcessed = new AtomicLong(0);
        this.totalBytesProcessed = new AtomicLong(0);
        this.isIngesting = false;
    }

    @Async
    public CompletableFuture<IngestionResult> ingestFileAsync(String filePath, String format) {
        IngestionResult result = ingestFile(filePath, format);
        return CompletableFuture.completedFuture(result);
    }

    public IngestionResult ingestFile(String filePath, String format) {
        long startTime = System.nanoTime();
        isIngesting = true;

        try {
            int numConsumers = Runtime.getRuntime().availableProcessors();
            Future<?>[] futures = new Future<?>[numConsumers];

            for (int i = 0; i < numConsumers; i++) {
                futures[i] = consumerPool.submit(new ChunkConsumer(format));
            }

            produceChunks(filePath);

            for (int i = 0; i < numConsumers; i++) {
                chunkQueue.put(LogChunk.createPoison());
            }

            for (Future<?> future : futures) {
                future.get();
            }

            long endTime = System.nanoTime();
            return new IngestionResult(
                    totalLinesProcessed.get(),
                    totalBytesProcessed.get(),
                    endTime - startTime
            );
        } catch (Exception e) {
            logger.error("Error during ingestion: {}", e.getMessage(), e);
            throw new RuntimeException("Ingestion failed", e);
        } finally {
            isIngesting = false;
        }
    }

    public IngestionResult ingestMultipartFile(MultipartFile file, String format) {
        try {
            File tempFile = File.createTempFile("loghawk_", ".log");
            file.transferTo(tempFile);
            IngestionResult result = ingestFile(tempFile.getAbsolutePath(), format);
            tempFile.delete();
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to process uploaded file", e);
        }
    }

    private void produceChunks(String filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder chunk = new StringBuilder();
            String line;
            long offset = 0;
            int chunkSize = 1024 * 1024; // 1MB chunks

            while ((line = reader.readLine()) != null) {
                chunk.append(line).append("\n");
                offset++;

                if (chunk.length() >= chunkSize) {
                    chunkQueue.put(new LogChunk(chunk.toString(), filePath, offset));
                    totalBytesProcessed.addAndGet(chunk.length());
                    chunk = new StringBuilder();
                }
            }

            if (chunk.length() > 0) {
                chunkQueue.put(new LogChunk(chunk.toString(), filePath, offset));
                totalBytesProcessed.addAndGet(chunk.length());
            }
        }
    }

    private class ChunkConsumer implements Runnable {
        private final String format;

        ChunkConsumer(String format) {
            this.format = format;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    LogChunk chunk = chunkQueue.take();
                    if (chunk.isPoison()) break;
                    processChunk(chunk);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void processChunk(LogChunk chunk) {
            LogParser parser = ParserFactory.getParser(format, chunk.getSourceFile());

            for (String line : chunk.getLines()) {
                try {
                    LogEntry entry = parser.parse(line);
                    indexBuilder.addEntry(entry);
                    totalLinesProcessed.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("Failed to parse line: {}", e.getMessage());
                }
            }
        }
    }

    public boolean isIngesting() {
        return isIngesting;
    }

    @Getter
    @Setter
    public static class IngestionResult {
        private final long linesProcessed;
        private final long bytesProcessed;
        private final long durationNanos;

        public IngestionResult(long linesProcessed, long bytesProcessed, long durationNanos) {
            this.linesProcessed = linesProcessed;
            this.bytesProcessed = bytesProcessed;
            this.durationNanos = durationNanos;
        }

        public double getDurationSeconds() { return durationNanos / 1000000000.0; }
        public double getThroughputMBps() {
            if (getDurationSeconds() == 0) return 0;
            return (bytesProcessed / 1000000.0) / getDurationSeconds();
        }
    }
}