package com.loghawk.parser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loghawk.model.LogEntry;
import com.loghawk.model.LogLevel;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class JsonLogParser implements LogParser {
    private final String sourceFile;
    private final AtomicLong lineCounter;

    public JsonLogParser(String sourceFile) {
        this.sourceFile = sourceFile;
        this.lineCounter = new AtomicLong(0);
    }

    @Override
    public LogEntry parse(String rawLine) {
        long lineNumber = lineCounter.incrementAndGet();

        try {
            JsonObject json = JsonParser.parseString(rawLine.trim()).getAsJsonObject();

            long timestamp = parseTimestamp(json);
            LogLevel level = json.has("level") ?
                    LogLevel.fromString(json.get("level").getAsString()) : LogLevel.INFO;
            String thread = json.has("thread") ?
                    json.get("thread").getAsString() : "main";
            String message = json.has("message") ?
                    json.get("message").getAsString() : rawLine;

            return new LogEntry(timestamp, level, thread, message, sourceFile, lineNumber);
        } catch (Exception e) {
            return new LogEntry(
                    System.currentTimeMillis(), LogLevel.INFO, "main",
                    rawLine, sourceFile, lineNumber
            );
        }
    }

    private long parseTimestamp(JsonObject json) {
        if (!json.has("timestamp")) return System.currentTimeMillis();

        String ts = json.get("timestamp").getAsString();
        try {
            return Instant.parse(ts).toEpochMilli();
        } catch (Exception e1) {
            try {
                return Long.parseLong(ts);
            } catch (NumberFormatException e2) {
                return System.currentTimeMillis();
            }
        }
    }

    @Override
    public String getFormatName() {
        return "JSON";
    }
}