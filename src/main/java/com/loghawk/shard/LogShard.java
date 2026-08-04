package com.loghawk.shard;

import com.loghawk.index.IndexBuilder;
import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.model.LogEntry;
import lombok.Getter;

@Getter
public class LogShard {
    private final String shardId;
    private final InvertedIndex invertedIndex;
    private final TimestampIndex timestampIndex;
    private final IndexBuilder indexBuilder;
    private final long startTimeRange;
    private final long endTimeRange;

    public LogShard(String shardId, long startTimeRange, long endTimeRange) {
        this.shardId = shardId;
        this.startTimeRange = startTimeRange;
        this.endTimeRange = endTimeRange;
        this.invertedIndex = new InvertedIndex();
        this.timestampIndex = new TimestampIndex();
        this.indexBuilder = new IndexBuilder(invertedIndex, timestampIndex);
    }

    public void addEntry(LogEntry entry) {
        if (isInRange(entry.getTimestamp())) {
            indexBuilder.addEntry(entry);
        }
    }

    public boolean isInRange(long timestamp) {
        return timestamp >= startTimeRange && timestamp < endTimeRange;
    }

    @Override
    public String toString() {
        return String.format("Shard[%s: %d - %d]", shardId, startTimeRange, endTimeRange);
    }
}