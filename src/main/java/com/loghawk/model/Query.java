package com.loghawk.model;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Query {
    private final String keyword;
    private final Long timeRangeStart;
    private final Long timeRangeEnd;
    private final Set<LogLevel> requiredLevels;
    private final String aggregationField;
    private final int maxResults;

    private Query(Builder builder) {
        this.keyword = builder.keyword;
        this.timeRangeStart = builder.timeRangeStart;
        this.timeRangeEnd = builder.timeRangeEnd;
        this.requiredLevels = builder.requiredLevels;
        this.aggregationField = builder.aggregationField;
        this.maxResults = builder.maxResults;
    }

    public boolean hasTimeRange() {
        return timeRangeStart != null && timeRangeEnd != null;
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    public static class Builder {
        private String keyword;
        private Long timeRangeStart;
        private Long timeRangeEnd;
        private Set<LogLevel> requiredLevels = new HashSet<>();
        private String aggregationField;
        private int maxResults = 1000;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder timeRange(long start, long end) {
            this.timeRangeStart = start;
            this.timeRangeEnd = end;
            return this;
        }

        public Builder addLevel(LogLevel level) {
            this.requiredLevels.add(level);
            return this;
        }

        public Builder levels(Set<LogLevel> levels) {
            this.requiredLevels = levels;
            return this;
        }

        public Builder aggregation(String field) {
            this.aggregationField = field;
            return this;
        }

        public Builder maxResults(int max) {
            this.maxResults = max;
            return this;
        }

        public Query build() {
            return new Query(this);
        }
    }
}