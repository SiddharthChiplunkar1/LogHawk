package com.loghawk.model;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class QueryResult {
    private final List<LogEntry> logEntryList;
    private final Map<String,Object> aggregations;
    private final Long queryTimeNanos;
    private final int totalMatches;
    private final int returnedMatches;
    private final int shardsSearched;

    public QueryResult(List<LogEntry> logEntryList,
                       Long queryTimeNanos,
                       int totalMatches,
                       int shardsSearched){
        this.logEntryList = logEntryList;
        this.aggregations = new HashMap<>();
        this.queryTimeNanos = queryTimeNanos;
        this.totalMatches = totalMatches;
        this.returnedMatches = logEntryList.size();
        this.shardsSearched = shardsSearched;
    }

    public void addAggregation(String key,Object value){
        this.aggregations.put(key,value);
    }

    public double getQueryTimeMillis(){
        return queryTimeNanos/1000000.0;
    }
}