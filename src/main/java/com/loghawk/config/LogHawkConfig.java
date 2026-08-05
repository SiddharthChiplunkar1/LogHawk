package com.loghawk.config;

import com.loghawk.coordinator.QueryCoordinator;
import com.loghawk.index.IndexBuilder;
import com.loghawk.index.InvertedIndex;
import com.loghawk.index.TimestampIndex;
import com.loghawk.shard.LogShard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@Configuration
public class LogHawkConfig {

    @Bean
    public InvertedIndex invertedIndex() {
        return new InvertedIndex();
    }

    @Bean
    public TimestampIndex timestampIndex() {
        return new TimestampIndex();
    }

    @Bean
    public IndexBuilder indexBuilder(InvertedIndex invertedIndex,
                                     TimestampIndex timestampIndex) {
        return new IndexBuilder(invertedIndex, timestampIndex);
    }

    @Bean
    public List<LogShard> shards() {
        List<LogShard> shards = new ArrayList<>();
        long now = System.currentTimeMillis();
        long hourInMillis = 3600000L;

        // Create 24 shards covering the PAST 24 hours (not future)
        long startOfRange = now - (24 * hourInMillis);
        for (int i = 0; i < 24; i++) {
            long startTime = startOfRange + (i * hourInMillis);
            long endTime = startTime + hourInMillis;
            shards.add(new LogShard("shard-" + i, startTime, endTime));
        }
        return shards;
    }

    @Bean
    public QueryCoordinator queryCoordinator(List<LogShard> shards) {
        return new QueryCoordinator(shards);
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, availableProcessors);
        int maxPoolSize = Math.max(corePoolSize * 2, 20);

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("LogHawk-");
        executor.initialize();
        return executor;
    }
}