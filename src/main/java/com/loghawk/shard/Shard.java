package com.loghawk.shard;

import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.model.LogEntry;
import com.loghawk.model.Query;
import com.loghawk.model.QueryResult;

public interface Shard {
    String getShardId();
    void addEntry(LogEntry entry);
    InvertedIndex getInvertedIndex();
    TimestampIndex getTimestampIndex();
    long getStartTimeRange();
    long getEndTimeRange();
    boolean isInRange(long timestamp);
    QueryResult search(Query query, boolean isBoolean);
}
