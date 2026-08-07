package com.loghawk.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loghawk.model.LogEntry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IndexPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(IndexPersistenceService.class);

    private final InvertedIndex invertedIndex;
    private final TimestampIndex timestampIndex;
    private final ObjectMapper objectMapper;

    @Value("${loghawk.index.data-dir:data/index}")
    private String dataDir;

    public IndexPersistenceService(InvertedIndex invertedIndex, TimestampIndex timestampIndex) {
        this.invertedIndex = invertedIndex;
        this.timestampIndex = timestampIndex;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void loadIndices() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File invertedFile = new File(dir, "inverted_index.json");
        File timestampFile = new File(dir, "timestamp_index.json");

        if (invertedIndex.getTotalTerms() == 0 && invertedFile.exists()) {
            try {
                logger.info("Loading InvertedIndex from disk...");
                Map<String, Set<Long>> data = objectMapper.readValue(invertedFile, new TypeReference<>() {});
                invertedIndex.restoreIndex(data);
                logger.info("InvertedIndex loaded successfully. Total terms: {}", invertedIndex.getTotalTerms());
            } catch (IOException e) {
                logger.error("Failed to load InvertedIndex: {}", e.getMessage(), e);
            }
        }

        if (timestampIndex.size() == 0 && timestampFile.exists()) {
            try {
                logger.info("Loading TimestampIndex from disk...");
                List<LogEntry> entries = objectMapper.readValue(timestampFile, new TypeReference<>() {});
                timestampIndex.addEntries(entries);
                logger.info("TimestampIndex loaded successfully. Total entries: {}", timestampIndex.size());
            } catch (IOException e) {
                logger.error("Failed to load TimestampIndex: {}", e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void saveOnShutdown() {
        logger.info("Shutting down... saving indices to disk.");
        saveIndices();
    }

    @Scheduled(fixedRateString = "${loghawk.index.save-interval:300000}")
    public void savePeriodically() {
        logger.debug("Running periodic index save...");
        saveIndices();
    }

    public synchronized void saveIndices() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File invertedFile = new File(dir, "inverted_index.json");
        File timestampFile = new File(dir, "timestamp_index.json");

        try {
            if (invertedIndex.getTotalTerms() > 0) {
                objectMapper.writeValue(invertedFile, invertedIndex.getRawIndex());
                logger.debug("Saved InvertedIndex to disk.");
            }
            if (timestampIndex.size() > 0) {
                objectMapper.writeValue(timestampFile, timestampIndex.getAllEntries());
                logger.debug("Saved TimestampIndex to disk.");
            }
        } catch (IOException e) {
            logger.error("Failed to save indices to disk: {}", e.getMessage(), e);
        }
    }
}
