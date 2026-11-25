package com.project.korea_investment_batch.shared.batch;

import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionJobParametersIntegrationTest extends BatchIntegrationSupport {
    private final ApplicationContext context;
    private final JobOperator operator;

    @Autowired
    CollectionJobParametersIntegrationTest(
            ApplicationContext context,
            JobOperator operator
    ) {
        this.context = context;
        this.operator = operator;
    }

    @ParameterizedTest
    @CsvSource({
        "marketMasterImportJob, collectionDate, inputRevision, UNKNOWN",
        "dartDisclosureImportJob, receiptDate, scanRevision, NASDAQ"
    })
    void rejectsInvalidParametersBeforeAnExecutionIsCreated(
            String jobName,
            String dateKey,
            String revisionKey,
            String unsupportedMarket
    ) {
        var job = context.getBean(jobName, Job.class);
        var market = new JobParameter<>("market", "KOSPI", String.class);
        var date = new JobParameter<>(dateKey, LocalDate.of(2026, 11, 20), LocalDate.class);
        var revision = new JobParameter<>(revisionKey, 1L, Long.class);

        assertRejected(job, "market", date, revision);
        assertRejected(job, dateKey, market, revision);
        assertRejected(job, revisionKey, market, date);
        assertRejected(job, "market", new JobParameter<>("market", unsupportedMarket, String.class), date, revision);
        assertRejected(job, revisionKey, market, date, new JobParameter<>(revisionKey, 0L, Long.class));
        assertRejected(job, revisionKey, market, date, new JobParameter<>(revisionKey, -1L, Long.class));
        assertRejected(job, "market", new JobParameter<>("market", 1L, Long.class), date, revision);
        assertRejected(job, dateKey, market, new JobParameter<>(dateKey, "2026-11-20", String.class), revision);
        assertRejected(job, revisionKey, market, date, new JobParameter<>(revisionKey, "1", String.class));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Integer.class)).isZero();
        assertThat(requests).isEmpty();
    }

    private void assertRejected(Job job, String parameterName, JobParameter<?>... parameters) {
        assertThatThrownBy(() -> operator.start(job, new JobParameters(Set.of(parameters))))
                .isInstanceOf(InvalidJobParametersException.class)
                .hasMessageContaining(parameterName);
    }
}
