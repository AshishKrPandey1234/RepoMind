package com.repomind.indexing;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IndexJobRecovery implements ApplicationRunner {
    private final IndexJobStore store;

    public IndexJobRecovery(IndexJobStore store) {
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        store.failInterruptedJobs();
    }
}
