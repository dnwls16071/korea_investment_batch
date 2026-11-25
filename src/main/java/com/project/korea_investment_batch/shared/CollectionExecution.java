package com.project.korea_investment_batch.shared;

import java.util.List;
import org.springframework.batch.core.BatchStatus;

public record CollectionExecution(long id, long instanceId, BatchStatus status, List<Step> steps) {

    public CollectionExecution {
        steps = List.copyOf(steps);
    }

    public boolean completed() {
        return status == BatchStatus.COMPLETED;
    }

    public record Step(String name, BatchStatus status, long commits, long reads, long writes, long filtered) {}
}
