package com.project.korea_investment_batch.dart.internal.job;

import com.project.korea_investment_batch.dart.internal.processing.DartDisclosureQueries;
import com.project.korea_investment_batch.dart.internal.DartMarkets;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.shared.batch.CollectionJobParametersValidator;
import com.project.korea_investment_batch.shared.batch.JobFailureListener;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.springframework.batch.core.configuration.annotation.JobParameter;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "dartJobConfiguration", proxyBeanMethods = false)
public class DartDisclosureImportJob {

    public static final String MARKET = "market";
    public static final String DATE = "receiptDate";
    public static final String REVISION = "scanRevision";

    @Bean
    @JobScope
    Supplier<CollectionRun> dartJobRun(
            @JobParameter(MARKET) Market market,
            @JobParameter(DATE) LocalDate date,
            @JobParameter(REVISION) long revision
    ) {
        var run = new CollectionRun(market, date, revision);
        return () -> run;
    }

    @Bean
    Job dartDisclosureImportJob(
            JobRepository repository,
            DartDisclosureQueries queries,
            @Qualifier("fetchDisclosurePages") Step fetch,
            @Qualifier("publishDisclosures") Step publish
    ) {
        return new JobBuilder("dartDisclosureImportJob", repository)
                .validator(new CollectionJobParametersValidator(DATE, REVISION, DartMarkets.SUPPORTED))
                .listener(failureListener(queries))
                .start(fetch)
                .next(publish)
                .build();
    }

    private JobFailureListener failureListener(DartDisclosureQueries queries) {
        return new JobFailureListener() {
            @Override
            protected void recordFailure(JobExecution execution, String step, String reason) {
                var parameters = execution.getJobParameters();
                queries.markIncomplete(parameters.getString(MARKET), parameters.getLocalDate(DATE),
                        parameters.getLong(REVISION), step, reason);
            }
        };
    }
}
