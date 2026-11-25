package com.project.korea_investment_batch.kis;

import com.project.korea_investment_batch.shared.CollectionExecution;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.batch.core.BatchStatus;

public interface KisModule {
    Map<Market, Result> collect(LocalDate date);

    CollectionExecution resume(CollectionRun run, boolean confirmedDead);

    Optional<CollectionExecution> execution(CollectionRun run);

    Optional<Input> input(CollectionRun run);

    Optional<Input> lastAttempt(Market market);

    Optional<Snapshot> latest(Market market);

    List<Instrument> instruments(Market market);

    List<Change> changes(Market market, LocalDate date);

    record Result(Long executionId, BatchStatus state, String error) {}

    record Input(long id, String archivePath, String archiveSha256, String parserVersion,
                 Instant collectedAt, LocalDate effectiveDate, Integer instrumentCount, String state,
                 String failureStep, String failureReason) {}

    record Snapshot(long id, LocalDate collectionDate, long inputRevision, Instant collectedAt,
                    LocalDate effectiveDate, int instrumentCount) {}

    record Instrument(String symbol, String standardCode, String name, String englishName, String instrumentType,
                      String securityCode, String productCode, Boolean suspended) {}

    record Change(String symbol, String changeType, String field, String instrumentType,
                  String oldValue, String newValue, Instant collectedAt, Instant publishedAt) {}
}
