package com.project.korea_investment_batch.dart.internal;

import com.project.korea_investment_batch.dart.internal.job.DartDisclosureImportJob;
import com.project.korea_investment_batch.dart.internal.processing.DartDisclosureQueries;
import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.batch.CollectionJobOperations;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class DartDisclosureCycleRunner implements DartModule {
    private final Job job;
    private final CollectionJobOperations operations;
    private final JdbcTemplate jdbc;
    private final DartDisclosureQueries queries;
    private final int lookbackDays;

    DartDisclosureCycleRunner(
            @Qualifier("dartDisclosureImportJob") Job job,
            CollectionJobOperations operations,
            JdbcTemplate jdbc,
            DartDisclosureQueries queries,
            DartProperties properties
    ) {
        this.job = job;
        this.operations = operations;
        this.jdbc = jdbc;
        this.queries = queries;
        this.lookbackDays = properties.lookbackDays();
    }

    @Override
    public void collect(LocalDate date) {
        for (int offset = 0; offset <= lookbackDays; offset++) {
            LocalDate receiptDate = date.minusDays(offset);
            for (Market market : DartMarkets.SUPPORTED) {
                try {
                    var execution = scan(market, receiptDate);
                    log.info("OpenDART {} {}: {} (execution {})", market, receiptDate, execution.status(), execution.id());
                } catch (RuntimeException e) {
                    log.warn("OpenDART {} {} could not run", market, receiptDate, e);
                }
            }
        }
    }

    @Override
    public CollectionExecution scan(Market market, LocalDate date) {
        DartMarkets.companyClass(market);
        return operations.exclusive(job, new CollectionRun(market, date, 1), () -> {
            long latest = queries.latest(market, date).map(Scan::scanRevision).orElse(1L);
            var run = new CollectionRun(market, date, latest);
            if (execution(run).filter(CollectionExecution::completed).isPresent()) {
                run = new CollectionRun(market, date, latest + 1);
            }
            return CollectionJobOperations.describe(execute(run, false));
        });
    }

    @Override
    public CollectionExecution resume(CollectionRun run, boolean confirmedDead) {
        DartMarkets.companyClass(run.market());
        return operations.exclusive(job, run, () -> CollectionJobOperations.describe(execute(run, confirmedDead)));
    }

    @Override
    public Optional<CollectionExecution> execution(CollectionRun run) {
        DartMarkets.companyClass(run.market());
        var parameters = CollectionJobOperations.parameters(run, DartDisclosureImportJob.DATE, DartDisclosureImportJob.REVISION);
        return operations.latest(job, parameters).map(CollectionJobOperations::describe);
    }

    @Override
    public Optional<Scan> scanResult(CollectionRun run) {
        DartMarkets.companyClass(run.market());
        return queries.scan(run);
    }

    @Override
    public Optional<Scan> latest(Market market, LocalDate date) {
        DartMarkets.companyClass(market);
        return queries.latest(market, date);
    }

    @Override
    public List<Disclosure> disclosures(Market market, LocalDate date) {
        DartMarkets.companyClass(market);
        return queries.disclosures(market, date);
    }

    private JobExecution execute(CollectionRun run, boolean confirmedDead) {
        jdbc.update("""
                INSERT INTO dart_scan (market, receipt_date, scan_revision) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, run.market().name(), run.date(), run.revision());
        return operations.run(job, CollectionJobOperations.parameters(run, DartDisclosureImportJob.DATE, DartDisclosureImportJob.REVISION),
                confirmedDead);
    }
}
