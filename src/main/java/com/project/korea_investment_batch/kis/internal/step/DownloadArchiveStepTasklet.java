package com.project.korea_investment_batch.kis.internal.step;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.kis.internal.processing.DownloadArchiveTasklet;
import com.project.korea_investment_batch.kis.internal.processing.MarketMasterQueries;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DownloadArchiveStepTasklet implements Tasklet, StepExecutionListener {
    private final CollectionRun run;
    private final MarketMasterQueries queries;
    private final DownloadArchiveTasklet downloader;
    private final CollectionCheckpoint checkpoint;
    private KisModule.Input input;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // Read before tasklet synchronization can retain the JDBC connection during file I/O.
        input = queries.input(run).orElseThrow();
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext context) throws Exception {
        downloader.download(run, input, checkpoint);
        return RepeatStatus.FINISHED;
    }
}
