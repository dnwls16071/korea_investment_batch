package com.project.korea_investment_batch.kis.internal;

import com.project.korea_investment_batch.kis.internal.job.MarketMasterImportJob;
import com.project.korea_investment_batch.kis.internal.processing.KisMasterFormats;
import com.project.korea_investment_batch.kis.internal.processing.MarketMasterQueries;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.batch.CollectionJobOperations;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MarketMasterCycleRunner implements KisModule {
    @Qualifier("marketMasterImportJob")
    private final Job job;
    private final CollectionJobOperations operations;
    private final JdbcTemplate jdbc;
    private final MarketMasterQueries queries;

    @Override
    public Map<Market, Result> collect(LocalDate date) {
        Map<Market, Result> results = new EnumMap<>(Market.class);
        for (Market market : KisMasterFormats.supportedMarkets()) {
            var run = new CollectionRun(market, date, 1);
            try {
                var execution = resume(run, false);
                String reason = queries.input(run).map(KisModule.Input::failureReason).orElse(null);
                results.put(market, new Result(execution.id(), execution.status(), reason));
            } catch (RuntimeException e) {
                results.put(market, new Result(null, BatchStatus.FAILED, e.getMessage()));
            }
        }
        return Map.copyOf(results);
    }

    @Override
    public CollectionExecution resume(CollectionRun run, boolean confirmedDead) {
        return operations.exclusive(job, run, () -> {
            jdbc.update("""
                    INSERT INTO kis_input (market, collection_date, input_revision) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE id = id
                    """, run.market().name(), run.date(), run.revision());
            return CollectionJobOperations.describe(operations.run(job,
                    CollectionJobOperations.parameters(run, MarketMasterImportJob.DATE, MarketMasterImportJob.REVISION), confirmedDead));
        });
    }

    @Override
    public Optional<CollectionExecution> execution(CollectionRun run) {
        return operations.latest(job, CollectionJobOperations.parameters(run, MarketMasterImportJob.DATE, MarketMasterImportJob.REVISION))
                .map(CollectionJobOperations::describe);
    }

    @Override
    public Optional<Input> input(CollectionRun run) {
        return queries.input(run);
    }

    @Override
    public Optional<Input> lastAttempt(Market market) {
        return queries.lastAttempt(market);
    }

    @Override
    public Optional<Snapshot> latest(Market market) {
        return queries.latest(market);
    }

    @Override
    public List<Instrument> instruments(Market market) {
        return queries.instruments(market);
    }

    @Override
    public List<Change> changes(Market market, LocalDate date) {
        return queries.changes(market, date);
    }
}
