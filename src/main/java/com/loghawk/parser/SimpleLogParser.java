package com.loghawk.parser;

import com.loghawk.model.LogEntry;
import com.loghawk.model.LogLevel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleLogParser implements LogParser {
    private static final Pattern LOG_PATTERN =
            Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\]" +
                    "\\s+(\\w+)\\s+\\[(.*?)\\]\\s+(.*)");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String sourceFile;
    private final AtomicLong lineCounter;

    public SimpleLogParser(String sourceFile) {
        this.sourceFile = sourceFile;
        this.lineCounter = new AtomicLong(0);
    }

    @Override
    public LogEntry parse(String rawLine) {
        long lineNumber = lineCounter.incrementAndGet();

        if (rawLine == null || rawLine.trim().isEmpty()) {
            return createDefaultEntry(rawLine, lineNumber);
        }

        Matcher matcher = LOG_PATTERN.matcher(rawLine.trim());

        if (matcher.matches()) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(matcher.group(1), FORMATTER);
                long timestamp = dateTime.atZone(ZoneId.systemDefault())
                                 .toInstant().toEpochMilli();
                LogLevel level = LogLevel.fromString(matcher.group(2));
                String thread = matcher.group(3);
                String message = matcher.group(4);

                return new LogEntry(timestamp, level,
                        thread, message, sourceFile, lineNumber);
            } catch (DateTimeParseException e) {
                // Fall through to default
            }
        }

        return createDefaultEntry(rawLine, lineNumber);
    }

    private LogEntry createDefaultEntry(String rawLine, long lineNumber) {
        return new LogEntry(
                System.currentTimeMillis(),
                LogLevel.INFO,
                "main",
                rawLine != null ? rawLine : "",
                sourceFile,
                lineNumber
        );
    }

    @Override
    public String getFormatName() {
        return "SIMPLE";
    }
}