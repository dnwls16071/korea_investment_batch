package com.project.korea_investment_batch.shared.batch;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@FunctionalInterface
public interface CollectionCheckpoint {
    void reached(String name);

    static void afterCommit(CollectionCheckpoint checkpoint, String name) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                checkpoint.reached(name);
            }
        });
    }
}
