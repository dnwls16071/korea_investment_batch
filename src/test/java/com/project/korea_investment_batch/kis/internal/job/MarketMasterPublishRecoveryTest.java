package com.project.korea_investment_batch.kis.internal.job;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MarketMasterPublishRecoveryTest extends BatchIntegrationSupport {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final KisModule kis;

    @Autowired
    MarketMasterPublishRecoveryTest(KisModule kis) {
        this.kis = kis;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptionPreservesThePublicationBoundaryAndReconcilesToOneSnapshot(boolean committed) throws Exception {
        var market = Market.KOSPI;
        baseline(market);
        var previous = kis.latest(market).orElseThrow();
        var changed = run(market, DATE.plusDays(1));
        update(market);
        try (var worker = worker("kis", changed, "kis.publish." + (committed ? "after-commit" : "before-commit"))) {
            worker.awaitCheckpoint();
            assertThat(kis.execution(changed).orElseThrow().status()).isEqualTo(BatchStatus.STARTED);
            if (committed) {
                assertThat(kis.latest(market).orElseThrow().collectionDate()).isEqualTo(changed.date());
                assertThat(kis.input(changed).orElseThrow().state()).isEqualTo("PUBLISHED");
                assertThat(kis.changes(market, changed.date())).hasSize(3);
            } else {
                assertThat(kis.latest(market)).contains(previous);
                assertThat(kis.input(changed).orElseThrow().state()).isEqualTo("VALIDATED");
                assertThat(kis.changes(market, changed.date())).isEmpty();
            }
        }
        var resumed = kis.resume(changed, true);
        assertThat(resumed.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(kis.input(changed).orElseThrow().state()).isEqualTo("PUBLISHED");
        var snapshot = kis.latest(market).orElseThrow();
        assertThat(snapshot.collectionDate()).isEqualTo(changed.date());
        assertThat(kis.instruments(market)).extracting(KisModule.Instrument::symbol, KisModule.Instrument::name)
                .containsExactly(tuple("000001", "새 이름"), tuple("000002", "신규 종목"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kis_snapshot WHERE input_id = ?", Integer.class,
                kis.input(changed).orElseThrow().id())).isEqualTo(1);
        assertThat(kis.changes(market, changed.date()))
                .extracting(KisModule.Change::symbol, KisModule.Change::changeType,
                KisModule.Change::field, KisModule.Change::oldValue, KisModule.Change::newValue)
                .containsExactly(tuple("000001", "NAME_CHANGED", "name", "기존 이름", "새 이름"),
                tuple("000001", "SUSPENSION_CHANGED", "suspended", "N", "Y"),
                tuple("000002", "NEW", "presence", "ABSENT", "PRESENT"));

        baseline(Market.KOSDAQ);
        update(Market.KOSDAQ);
        assertThat(kis.resume(run(Market.KOSDAQ, changed.date()), false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(kis.instruments(market)).isEqualTo(kis.instruments(Market.KOSDAQ));
        assertThat(historyValues(market, changed.date())).isEqualTo(historyValues(Market.KOSDAQ, changed.date()));
    }

    @Test
    void laterPublicationPreventsAnEarlierFailedSnapshotFromBecomingCurrent() {
        var market = Market.KOSPI;
        baseline(market);
        update(market);
        var failed = run(market, DATE.plusDays(1));
        fault.failAt("kis.publish.before-commit");
        assertThat(kis.resume(failed, false).status()).isEqualTo(BatchStatus.FAILED);
        assertThat(kis.changes(market, failed.date())).isEmpty();
        update(market);
        assertThat(kis.resume(run(market, DATE.plusDays(2)), false).status()).isEqualTo(BatchStatus.COMPLETED);
        var newest = kis.latest(market).orElseThrow();
        assertThat(kis.resume(failed, false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(kis.input(failed).orElseThrow().state()).isEqualTo("SUPERSEDED");
        assertThat(kis.latest(market)).contains(newest);
        assertThat(kis.changes(market, failed.date())).isEmpty();
    }

    @Test
    void recordsMissingAndReappearedOnlyWhenPresenceChanges() {
        var market = Market.KOSPI;
        baseline(market);
        update(market);
        kis.resume(run(market, DATE.plusDays(1)), false);
        kis.resume(run(market, DATE.plusDays(2)), false);
        assertThat(kis.changes(market, DATE)).isEmpty();
        assertThat(kis.changes(market, DATE.plusDays(2))).isEmpty();

        archive(market, domestic(market, "000001", "새 이름", "ST", ' ', 'Y'));
        kis.resume(run(market, DATE.plusDays(3)), false);
        assertThat(kis.changes(market, DATE.plusDays(3))).extracting(KisModule.Change::changeType)
                .containsExactly("MISSING");
        update(market);
        kis.resume(run(market, DATE.plusDays(4)), false);
        assertThat(kis.changes(market, DATE.plusDays(4))).extracting(KisModule.Change::changeType)
                .containsExactly("REAPPEARED");
    }

    private void baseline(Market market) {
        archive(market, domestic(market, "000001", "기존 이름", "ST", ' ', 'N'));
        assertThat(kis.resume(run(market, DATE), false).status()).isEqualTo(BatchStatus.COMPLETED);
    }

    private static void update(Market market) {
        archive(market, domestic(market, "000001", "새 이름", "ST", ' ', 'Y'),
                domestic(market, "000002", "신규 종목", "EF", '2', 'N'));
    }

    private List<List<String>> historyValues(Market market, LocalDate date) {
        return kis.changes(market, date).stream().map(change ->
                List.of(change.symbol(), change.changeType(), change.field(), change.oldValue(), change.newValue())).toList();
    }
}
