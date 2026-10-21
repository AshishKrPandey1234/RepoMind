package com.repomind.indexing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class IndexingConfiguration {

    @Bean(name = "indexingExecutor")
    Executor indexingExecutor(IndexingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.executor().corePoolSize());
        executor.setMaxPoolSize(properties.executor().maxPoolSize());
        executor.setQueueCapacity(properties.executor().queueCapacity());
        executor.setThreadNamePrefix("repomind-index-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
