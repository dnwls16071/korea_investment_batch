package com.project.korea_investment_batch.operations;

import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

@DisallowConcurrentExecution
@RequiredArgsConstructor
public class DailyCollectionQuartzJob implements Job {
    private final DailyCollectionScheduler collection;

    @Override
    public void execute(JobExecutionContext context) {
        collection.collect();
    }
}
