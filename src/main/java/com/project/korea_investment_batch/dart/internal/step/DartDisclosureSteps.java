package com.project.korea_investment_batch.dart.internal.step;

import com.project.korea_investment_batch.dart.internal.DartProperties;
import com.project.korea_investment_batch.dart.internal.processing.DartDisclosureQueries;
import com.project.korea_investment_batch.dart.internal.processing.DartPageReader;
import com.project.korea_investment_batch.dart.internal.processing.DartPageStore;
import com.project.korea_investment_batch.dart.internal.processing.DartPageValidation;
import com.project.korea_investment_batch.dart.internal.processing.DisclosurePage;
import com.project.korea_investment_batch.dart.internal.processing.PublishDisclosuresTasklet;
import com.project.korea_investment_batch.dart.remote.OpenDartClient;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.batch.CollectionCheckpoint;
import java.util.function.Supplier;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class DartDisclosureSteps {
    private final JobRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final DartDisclosureQueries queries;
    private final CollectionCheckpoint checkpoint;
    private final Supplier<CollectionRun> jobRun;

    DartDisclosureSteps(
            JobRepository repository,
            PlatformTransactionManager transactionManager,
            DartDisclosureQueries queries,
            ObjectProvider<CollectionCheckpoint> checkpoints,
            @Qualifier("dartJobRun") Supplier<CollectionRun> jobRun
    ) {
        this.repository = repository;
        this.transactionManager = transactionManager;
        this.queries = queries;
        this.checkpoint = checkpoints.getIfAvailable(() -> name -> {});
        this.jobRun = jobRun;
    }

    @Bean
    Step fetchDisclosurePages(
            DartPageReader dartPageReader,
            DartPageValidation dartPageProcessor,
            DartPageStore store
    ) {
        return new StepBuilder("fetchDisclosurePages", repository)
                .<DisclosurePage, DisclosurePage.Validated>chunk(1)
                .transactionManager(new ResourcelessTransactionManager())
                .reader(dartPageReader)
                .processor(dartPageProcessor)
                .writer(chunk -> {
                    for (var page : chunk) {
                        store.write(page);
                        checkpoint.reached("dart.fetch.after-page");
                    }
                })
                .build();
    }

    @Bean
    @StepScope
    DartPageReader dartPageReader(DartPageStore store, OpenDartClient client) {
        return new DartPageReader(jobRun.get(), queries, store, client);
    }

    @Bean
    @StepScope
    DartPageValidation dartPageProcessor(DartProperties properties) {
        return new DartPageValidation(jobRun.get(), properties.pageSize());
    }

    @Bean
    Step publishDisclosures(PublishDisclosuresTasklet publisher) {
        return new StepBuilder("publishDisclosures", repository).tasklet((contribution, context) -> {
            var run = jobRun.get();
            publisher.publish(run, queries.scan(run).orElseThrow(), checkpoint);
            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }
}
