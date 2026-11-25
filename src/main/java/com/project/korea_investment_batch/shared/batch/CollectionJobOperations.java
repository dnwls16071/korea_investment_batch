package com.project.korea_investment_batch.shared.batch;

import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.CollectionRun;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionJobOperations {

    private final JobOperator operator;
    private final JobRepository repository;
    private final JdbcTemplate jdbc;

    public Optional<JobExecution> latest(Job job, JobParameters parameters) {
        return Optional.ofNullable(repository.getLastJobExecution(job.getName(), parameters));
    }

    public static JobParameters parameters(CollectionRun run, String dateKey, String revisionKey) {
        return new JobParametersBuilder().addString("market", run.market().name())
                .addLocalDate(dateKey, run.date()).addLong(revisionKey, run.revision()).toJobParameters();
    }

    public static CollectionExecution describe(JobExecution execution) {
        var steps = execution.getStepExecutions().stream()
                .sorted(Comparator.comparingLong(StepExecution::getId))
                .map(step -> {
                    return new CollectionExecution.Step(
                            step.getStepName(),
                            step.getStatus(),
                            step.getCommitCount(),
                            step.getReadCount(),
                            step.getWriteCount(),
                            step.getFilterCount());
                })
                .toList();
        return new CollectionExecution(execution.getId(), execution.getJobInstance().getId(),
                execution.getStatus(), steps);
    }

    // The dedicated connection holds the same date/market lock across work transactions.
    // A dead worker's connection is closed by MySQL, releasing this lock for explicit recovery.
    public <T> T exclusive(Job job, CollectionRun run, Supplier<T> body) {
        String scope = job.getName() + "/" + run.market() + "/" + run.date();
        return jdbc.execute((ConnectionCallback<T>) connection -> {
            if (lock(connection, "GET_LOCK", ", 0", scope) != 1) {
                throw new IllegalStateException("A collection process is still active for " + scope);
            }
            try {
                return body.get();
            } finally {
                lock(connection, "RELEASE_LOCK", "", scope);
            }
        });
    }

    public JobExecution run(Job job, JobParameters parameters, boolean confirmedDead) {
        JobExecution previous = latest(job, parameters).orElse(null);
        try {
            if (previous == null) {
                return operator.start(job, parameters);
            }
            if (previous.getStatus() == BatchStatus.COMPLETED) {
                return previous;
            }
            if (previous.isRunning() || previous.isStopping()) {
                if (!confirmedDead) {
                    throw new IllegalStateException("Confirm the previous process has exited before recovering execution "
                            + previous.getId());
                }
                previous = operator.recover(previous);
            }
            return operator.restart(previous);
        } catch (org.springframework.batch.core.job.JobExecutionException e) {
            throw new IllegalStateException("Unable to execute " + job.getName(), e);
        }
    }

    private static int lock(Connection connection, String function, String arguments, String scope)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT " + function + "(SHA2(CONCAT(DATABASE(), '/', ?), 256)" + arguments + ")")) {
            statement.setString(1, scope);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
