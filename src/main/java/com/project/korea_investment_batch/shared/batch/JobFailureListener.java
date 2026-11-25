package com.project.korea_investment_batch.shared.batch;

import java.util.Comparator;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;

public abstract class JobFailureListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution execution) {
        if (execution.getStatus() != BatchStatus.FAILED) {
            return;
        }
        var step = execution.getStepExecutions().stream().max(Comparator.comparingLong(StepExecution::getId));
        Throwable cause = execution.getAllFailureExceptions().stream().findFirst().orElse(null);
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String reason = cause == null || cause.getMessage() == null ? "Batch execution failed" : cause.getMessage();
        recordFailure(execution, step.map(StepExecution::getStepName).orElse(null),
                reason.substring(0, Math.min(1000, reason.length())));
    }

    protected abstract void recordFailure(JobExecution execution, String step, String reason);
}
