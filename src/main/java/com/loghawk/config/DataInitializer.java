package com.loghawk.config;

import com.loghawk.index.TimestampIndex;
import com.loghawk.service.LogIngestionService;
import com.loghawk.util.LogDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final TimestampIndex timestampIndex;
    private final LogIngestionService logIngestionService;

    public DataInitializer(TimestampIndex timestampIndex, LogIngestionService logIngestionService) {
        this.timestampIndex = timestampIndex;
        this.logIngestionService = logIngestionService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (timestampIndex.size() == 0) {
            logger.info("TimestampIndex is empty. Starting auto-ingestion of sample data...");
            
            String sampleFilePath = "sample-logs/application.log";
            logger.info("Generating 500,000 lines of sample logs...");
            File sampleFile = LogDataGenerator.generateSampleFile(sampleFilePath, 500_000, 7);
            
            logger.info("Ingesting generated sample data from {}", sampleFile.getAbsolutePath());
            LogIngestionService.IngestionResult result = logIngestionService.ingestFile(sampleFile.getAbsolutePath(), "SIMPLE");
            
            logger.info("Auto-ingestion complete!");
            logger.info("Lines Processed: {}", result.getLinesProcessed());
            logger.info("Duration: {} seconds", String.format("%.2f", result.getDurationSeconds()));
            logger.info("Throughput: {} MB/s", String.format("%.2f", result.getThroughputMBps()));
        } else {
            logger.info("Indices already contain data. Skipping auto-ingestion.");
        }
    }
}
