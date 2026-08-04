package com.loghawk.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogLevel {
    DEBUG(0,"DEBUG"),
    INFO(1,"INFO"),
    WARN(2,"WARN"),
    ERROR(3,"ERROR"),
    FATAL(4,"FATAL");

    private final int severity;
    private final String label;

    public static LogLevel fromString(String level){
        if(level == null)
            return INFO;

        return switch(level.toUpperCase()) {
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN" -> WARN;
            case "ERROR" -> ERROR;
            case "FATAL" ,"CRITICAL" -> FATAL;
            default -> INFO;
        };
    }
}
