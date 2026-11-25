package com.project.korea_investment_batch.kis.internal.step;

import com.project.korea_investment_batch.kis.internal.KisProperties;
import com.project.korea_investment_batch.kis.internal.processing.BatchInputStore;
import com.project.korea_investment_batch.kis.internal.processing.DownloadArchiveTasklet;
import com.project.korea_investment_batch.kis.internal.processing.InstrumentStaging;
import com.project.korea_investment_batch.kis.internal.processing.InstrumentStaging.StagedInstrument;
import com.project.korea_investment_batch.kis.internal.processing.MarketMasterQueries;
import com.project.korea_investment_batch.kis.internal.processing.PublishSnapshotTasklet;
import com.project.korea_investment_batch.kis.internal.processing.ValidateSnapshotTasklet;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class MarketMasterSteps {
    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;

    private final JobRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final MarketMasterQueries queries;
    private final CollectionCheckpoint checkpoint;
    private final Supplier<CollectionRun> jobRun;

    MarketMasterSteps(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            MarketMasterQueries queries,
            ObjectProvider<CollectionCheckpoint> checkpoints,
            @Qualifier("kisJobRun") Supplier<CollectionRun> jobRun
    ) {
        this.repository = repository;
        this.transactionManager = transactionManager;
        this.queries = queries;
        this.checkpoint = checkpoints.getIfAvailable(() -> name -> {});
        this.jobRun = jobRun;
    }

    @Bean
    Step downloadArchive(DownloadArchiveStepTasklet tasklet) {
        return new StepBuilder("downloadArchive", repository)
                .tasklet(tasklet, new ResourcelessTransactionManager())
                .build();
    }

    @Bean
    @StepScope
    DownloadArchiveStepTasklet kisDownloadArchiveTasklet(DownloadArchiveTasklet downloader) {
        return new DownloadArchiveStepTasklet(jobRun.get(), queries, downloader, checkpoint);
    }

    @Bean
    Step stageInstruments(
            KisProperties properties,
            FlatFileItemReader<StagedInstrument> kisArchiveReader,
            ItemWriter<StagedInstrument> kisStageWriter
    ) {
        return new StepBuilder("stageInstruments", repository)
                .<StagedInstrument, StagedInstrument>chunk(properties.chunkSize())
                .transactionManager(transactionManager)
                .reader(kisArchiveReader)
                .processor(row -> row.supported() ? row : null)
                .writer(kisStageWriter)
                .faultTolerant()
                .retryPolicy(RetryPolicy.builder()
                        .maxRetries(2)
                        .delay(Duration.ofMillis(200))
                        .predicate(MarketMasterSteps::isLockWaitTimeout)
                        .build())
                .build();
    }

    private static boolean isLockWaitTimeout(Throwable failure) {
        // A writer retry runs inside the chunk transaction, which MySQL deadlocks roll back entirely.
        return failure instanceof DataAccessException dataAccess
                && dataAccess.getMostSpecificCause() instanceof SQLException sql
                && sql.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT;
    }

    @Bean
    @StepScope
    FlatFileItemReader<StagedInstrument> kisArchiveReader(BatchInputStore store) {
        var run = jobRun.get();
        return InstrumentStaging.reader(run, queries.input(run).orElseThrow(), store);
    }

    @Bean
    @StepScope
    ItemWriter<StagedInstrument> kisStageWriter(JdbcTemplate jdbc) {
        long inputId = queries.input(jobRun.get()).orElseThrow().id();
        return chunk -> InstrumentStaging.write(jdbc, inputId, chunk, checkpoint);
    }

    @Bean
    Step validateSnapshot(ValidateSnapshotTasklet validation) {
        return taskletStep("validateSnapshot", (contribution, context) -> {
            var run = jobRun.get();
            validation.validate(run, queries.input(run).orElseThrow().id());
            return RepeatStatus.FINISHED;
        });
    }

    @Bean
    Step publishSnapshot(PublishSnapshotTasklet publisher, ValidateSnapshotTasklet validation) {
        return taskletStep("publishSnapshot", (contribution, context) -> {
            var run = jobRun.get();
            publisher.publish(run, queries.input(run).orElseThrow(), validation, checkpoint);
            return RepeatStatus.FINISHED;
        });
    }

    private Step taskletStep(String name, Tasklet tasklet) {
        return new StepBuilder(name, repository).tasklet(tasklet, transactionManager).build();
    }
}
