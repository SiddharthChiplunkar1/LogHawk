package com.loghawk.parser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParserFactory {
    private static final Map<String, LogParser> parserCache = new ConcurrentHashMap<>();

    public static LogParser getParser(String format, String sourceFile) {
        String cacheKey = format.toUpperCase() + ":" + sourceFile;
        return parserCache.computeIfAbsent(cacheKey,
                key -> createParser(format, sourceFile));
    }

    private static LogParser createParser(String format, String sourceFile) {
        return switch (format.toUpperCase()) {
            case "JSON" -> new JsonLogParser(sourceFile);
            case "SIMPLE" -> new SimpleLogParser(sourceFile);
            default -> new SimpleLogParser(sourceFile);
        };
    }

    public static void clearCache() {
        parserCache.clear();
    }
}