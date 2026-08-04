package com.loghawk.index;

import com.loghawk.model.LogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimestampIndex {
    private final CopyOnWriteArrayList<LogEntry> logEntries;
    private volatile boolean needsSort;

    public TimestampIndex(){
        this.logEntries = new CopyOnWriteArrayList<>();
        this.needsSort = false;
    }

    public void addEntry(LogEntry logEntry){
        logEntries.add(logEntry);
        needsSort = true;
    }

    public void addEntries(List<LogEntry> logEntryList){
        logEntries.addAll(logEntryList);
        needsSort = true;
    }

    public List<LogEntry> searchByTimeRange(Long startTime,Long endTime){
        ensureSorted();

        int startIndex = findStartIndex(startTime);
        int endIndex = findEndIndex(endTime);

        if(startIndex == -1 || endIndex == -1 || startIndex > endIndex)
            return Collections.emptyList();

        return new ArrayList<>(logEntries.subList(startIndex,endIndex+1));
    }

    private int findStartIndex(Long targetTime){
        int left = 0;
        int right = logEntries.size() - 1;
        int result = -1;
        while(left <= right){
            int mid = left + (right-left)/2;
            if(logEntries.get(mid).getTimestamp() >= targetTime){
                result = mid;
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        return result;
    }

    private int findEndIndex(Long targetTime){
        int left = 0;
        int right = logEntries.size() - 1;
        int result = -1;
        while(left <= right){
            int mid = left + (right-left)/2;
            if(logEntries.get(mid).getTimestamp() <= targetTime){
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return result;
    }

    private synchronized void ensureSorted(){
        if(needsSort){
            logEntries.sort(LogEntry::compareTo);
            needsSort = false;
        }
    }

    public int size(){
        return logEntries.size();
    }

    public List<LogEntry> getAllEntries(){
        ensureSorted();
        return new ArrayList<>(logEntries);
    }
}