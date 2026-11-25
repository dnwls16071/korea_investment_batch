package com.project.korea_investment_batch.kis.internal.job;

import com.project.korea_investment_batch.kis.internal.processing.KisMasterFormats;
import com.project.korea_investment_batch.kis.internal.processing.MarketMasterQueries;
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

@Configuration(value = "marketMasterJobConfiguration", proxyBeanMethods = false)
public class MarketMasterImportJob {
    public static final String DATE = "collectionDate";
    public static final String REVISION = "inputRevision";

    @Bean
    Job marketMasterImportJob(
            JobRepository repository,
            MarketMasterQueries queries,
            @Qualifier("downloadArchive") Step download,
            @Qualifier("stageInstruments") Step stage,
            @Qualifier("validateSnapshot") Step validate,
            @Qualifier("publishSnapshot") Step publish
    ) {
        return new JobBuilder("marketMasterImportJob", repository)
                .validator(new CollectionJobParametersValidator(DATE, REVISION, KisMasterFormats.supportedMarkets()))
                .listener(failureListener(queries))
                .start(download)
                .next(stage)
                .next(validate)
                .next(publish)
                .build();
    }

    @Bean
    @JobScope
    Supplier<CollectionRun> kisJobRun(
            @JobParameter("market") Market market,
            @JobParameter(DATE) LocalDate date,
            @JobParameter(REVISION) long revision
    ) {
        var run = new CollectionRun(market, date, revision);
        return () -> run;
    }

    private JobFailureListener failureListener(MarketMasterQueries queries) {
        return new JobFailureListener() {
            @Override
            protected void recordFailure(JobExecution execution, String step, String reason) {
                var parameters = execution.getJobParameters();
                var run = new CollectionRun(Market.valueOf(parameters.getString("market")),
                        parameters.getLocalDate(DATE), parameters.getLong(REVISION));
                queries.markFailed(run, step, reason);
            }
        };
    }
}
