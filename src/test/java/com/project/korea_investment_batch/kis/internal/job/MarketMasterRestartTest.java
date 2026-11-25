package com.project.korea_investment_batch.kis.internal.job;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketMasterRestartTest extends BatchIntegrationSupport {
    private final KisModule kis;
    private final DataSource dataSource;

    @Autowired
    MarketMasterRestartTest(
            KisModule kis,
            DataSource dataSource
    ) {
        this.kis = kis;
        this.dataSource = dataSource;
    }

    @Test
    void holdsOnlyTheJobLockConnectionWhileDownloadingTheArchive() throws Exception {
        var run = run(Market.KOSPI, LocalDate.of(2026, 11, 20));
        archive(run.market(), row("000001"));
        var path = archivePath(run.market());
        var response = responses.get(path);
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        var activeConnections = new AtomicInteger();
        responses.put(path, request -> {
            activeConnections.set(pool.getActiveConnections());
            return response.apply(request);
        });

        assertThat(kis.resume(run, false).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(activeConnections).hasValue(1);
    }

    @Test
    void rollsBackArchiveMetadataAndReusesTheFrozenFileOnRestart() {
        var run = run(Market.KOSPI, LocalDate.of(2026, 11, 20));
        archive(run.market(), row("000001"));
        fault.failAt("kis.archive.before-metadata-commit");

        var failed = kis.resume(run, false);
        assertThat(failed.status()).isEqualTo(BatchStatus.FAILED);
        var input = kis.input(run).orElseThrow();
        assertThat(input.archivePath()).isNull();
        assertThat(input.archiveSha256()).isNull();

        requests.clear();
        responses.remove(archivePath(run.market()));
        var recovered = kis.resume(run, false);
        assertThat(recovered.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recovered.instanceId()).isEqualTo(failed.instanceId());
        assertThat(kis.input(run).orElseThrow().archiveSha256()).isNotBlank();
        assertThat(requests).isEmpty();
    }

    @Test
    void restoresFrozenArchiveAndEffectiveDateWhenDatabaseReferenceWasNotCommitted() throws Exception {
        var run = run(Market.KOSPI, LocalDate.of(2026, 11, 20));
        archive(run.market(), Map.of("X-Source-Effective-Date", "2026-11-19"), row("000001"));
        try (var worker = worker("kis", run, "kis.archive.after-freeze")) {
            worker.awaitCheckpoint();
            assertThat(kis.input(run).orElseThrow().archivePath()).isNull();
        }

        requests.clear();
        responses.remove(archivePath(run.market()));
        assertThat(kis.resume(run, true).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(requests).isEmpty();
        assertThat(kis.latest(run.market()).orElseThrow().effectiveDate()).isEqualTo(run.date().minusDays(1));
        assertThat(kis.instruments(run.market())).extracting(KisModule.Instrument::symbol)
                .containsExactly("000001");
    }

    @Test
    void resumesAtCommittedChunkUsingOriginalArchiveAndRestartsNewRevisionFromTheBeginning() throws Exception {
        var run = run(Market.KOSPI, LocalDate.of(2026, 11, 20));
        archive(run.market(), row("000001"), row("000002"), row("000003"), row("000004"));
        String originalDigest;
        long instance;
        long execution;
        try (var worker = worker("kis", run, "kis.stage.after-commit")) {
            worker.awaitCheckpoint();
            var active = kis.execution(run).orElseThrow();
            assertThat(active.status()).isEqualTo(BatchStatus.STARTED);
            instance = active.instanceId();
            execution = active.id();
            var input = kis.input(run).orElseThrow();
            originalDigest = input.archiveSha256();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM kis_stage WHERE input_id = ?", Integer.class, input.id()))
                    .isEqualTo(2);
            assertThatThrownBy(() -> kis.resume(run, true)).hasMessageContaining("still active");
        }

        archive(run.market(), row("000005"), row("000006"), row("000007"), row("000008"));
        requests.clear();
        assertThatThrownBy(() -> kis.resume(run, false)).hasMessageContaining("previous process");
        var recovered = kis.resume(run, true);
        assertThat(recovered.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recovered.instanceId()).isEqualTo(instance);
        assertThat(recovered.id()).isNotEqualTo(execution);
        assertThat(recovered.steps()).filteredOn(step -> step.name().equals("stageInstruments"))
                .singleElement().satisfies(step -> {
                    assertThat(step.status()).isEqualTo(BatchStatus.COMPLETED);
                    assertThat(step.reads()).isEqualTo(2);
                });
        assertThat(kis.input(run).orElseThrow().archiveSha256()).isEqualTo(originalDigest);
        assertThat(requests).isEmpty();
        assertThat(kis.instruments(run.market())).extracting(KisModule.Instrument::symbol)
                .containsExactly("000001", "000002", "000003", "000004");

        var replacement = new CollectionRun(run.market(), run.date(), 2);
        var newExecution = kis.resume(replacement, false);
        assertThat(newExecution.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(newExecution.instanceId()).isNotEqualTo(instance);
        assertThat(kis.input(replacement).orElseThrow().archiveSha256()).isNotEqualTo(originalDigest);
        assertThat(kis.instruments(run.market())).extracting(KisModule.Instrument::symbol)
                .containsExactly("000005", "000006", "000007", "000008");
    }

    private static String row(String symbol) {
        return domestic(Market.KOSPI, symbol, "종목 " + symbol, "ST", ' ', 'N');
    }
}
