package com.loghawk.index;

import com.loghawk.model.LogEntry;
import org.springframework.stereotype.Component;

@Component
public class IndexBuilder {
    private final InvertedIndex invertedIndex;
    private final TimestampIndex timestampIndex;

    public IndexBuilder(InvertedIndex invertedIndex,
                        TimestampIndex timestampIndex){
        this.invertedIndex = invertedIndex;
        this.timestampIndex = timestampIndex;
    }

    public void addEntry(LogEntry entry) {
        invertedIndex.addEntry(entry);
        timestampIndex.addEntry(entry);
    }

    public InvertedIndex getInvertedIndex(){
        return invertedIndex;
    }

    public TimestampIndex getTimestampIndex() {
        return timestampIndex;
    }
}