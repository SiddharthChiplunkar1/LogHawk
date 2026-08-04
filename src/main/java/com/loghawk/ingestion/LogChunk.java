package com.loghawk.ingestion;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class LogChunk {
    private final String data;
    private final String sourceFile;
    private final long startOffset;
    private final boolean isPoison;

    public LogChunk(String data, String sourceFile, long startOffset) {
        this.data = data;
        this.sourceFile = sourceFile;
        this.startOffset = startOffset;
        this.isPoison = false;
    }

    private LogChunk(boolean isPoison) {
        this.data = null;
        this.sourceFile = null;
        this.startOffset = -1;
        this.isPoison = isPoison;
    }

    public static LogChunk createPoison() {
        return new LogChunk(true);
    }

    public List<String> getLines() {
        if (data == null) return List.of();
        return Arrays.stream(data.split("\n"))
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.toList());
    }
}