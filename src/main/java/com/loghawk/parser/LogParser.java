package com.loghawk.parser;

import com.loghawk.model.LogEntry;

public interface LogParser {
    LogEntry parse(String rawLine);
    String getFormatName();
}