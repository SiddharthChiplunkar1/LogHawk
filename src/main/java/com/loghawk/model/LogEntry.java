package com.loghawk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class LogEntry implements Comparable<LogEntry> {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long id;
    private final long timestamp;
    private final LogLevel level;
    private final String thread;
    private final String message;
    private final String sourceFile;
    private final long lineNumber;

    public LogEntry(long timestamp, LogLevel level, String thread,
                    String message, String sourceFile, long lineNumber) {
        this.id = timestamp * 10000 + lineNumber;
        this.timestamp = timestamp;
        this.level = level;
        this.thread = thread != null ? thread : "main";
        this.message = message;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
    }

    public String getFormattedTimestamp() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(FORMATTER);
    }

    @Override
    public int compareTo(LogEntry other) {
        int timeCompare = Long.compare(this.timestamp, other.timestamp);
        if (timeCompare != 0) return timeCompare;
        return Long.compare(this.id, other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEntry logEntry = (LogEntry) o;
        return id == logEntry.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("[%s] %-5s [%s] %s",
                getFormattedTimestamp(), level, thread, message);
    }
}