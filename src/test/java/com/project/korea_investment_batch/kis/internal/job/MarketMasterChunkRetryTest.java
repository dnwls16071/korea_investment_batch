package com.project.korea_investment_batch.kis.internal.job;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 1")
@MockitoSpyBean(types = JdbcTemplate.class)
class MarketMasterChunkRetryTest extends BatchIntegrationSupport {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final KisModule kis;
    private final DataSource dataSource;

    @Autowired
    MarketMasterChunkRetryTest(
            KisModule kis,
            DataSource dataSource
    ) {
        this.kis = kis;
        this.dataSource = dataSource;
    }

    @Test
    void retriesAChunkTwiceAndCommitsItsRowsOnceWhenTheLockIsReleased() throws SQLException {
        var run = run(Market.KOSPI, DATE);
        archive(run.market(), row("000001"), row("000002"), row("000003"), row("000004"));

        try (var blocker = dataSource.getConnection()) {
            var attempts = lockSecondChunk(blocker, true);
            var execution = kis.resume(run, false);
            assertThat(execution.status()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(attempts).hasValue(3);
            assertThat(execution.steps()).filteredOn(step -> step.name().equals("stageInstruments"))
                    .singleElement().satisfies(step -> {
                        assertThat(step.reads()).isEqualTo(4);
                        assertThat(step.writes()).isEqualTo(4);
                    });
        }
        assertThat(kis.instruments(run.market())).extracting(KisModule.Instrument::symbol)
                .containsExactly("000001", "000002", "000003", "000004");
    }

    @Test
    void retainsThePreviousCheckpointWhenTheRetryLimitIsExhausted() throws SQLException {
        var run = run(Market.KOSPI, DATE);
        archive(run.market(), row("000001"), row("000002"), row("000003"), row("000004"));

        long instanceId;
        try (var blocker = dataSource.getConnection()) {
            var attempts = lockSecondChunk(blocker, false);
            var execution = kis.resume(run, false);
            assertThat(execution.status()).isEqualTo(BatchStatus.FAILED);
            assertThat(attempts).hasValue(3);
            instanceId = execution.instanceId();
            var input = kis.input(run).orElseThrow();
            assertThat(input.failureStep()).isEqualTo("stageInstruments");
            assertThat(jdbc.queryForList("SELECT original_row_no FROM kis_stage WHERE input_id = ? ORDER BY original_row_no",
                    Long.class, input.id())).containsExactly(1L, 2L);
        }

        requests.clear();
        responses.remove(archivePath(run.market()));
        var recovered = kis.resume(run, false);
        assertThat(recovered.status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(recovered.instanceId()).isEqualTo(instanceId);
        assertThat(recovered.steps()).filteredOn(step -> step.name().equals("stageInstruments"))
                .singleElement().satisfies(step -> {
                    assertThat(step.reads()).isEqualTo(2);
                    assertThat(step.writes()).isEqualTo(2);
                });
        assertThat(requests).isEmpty();
        assertThat(kis.instruments(run.market())).extracting(KisModule.Instrument::symbol)
                .containsExactly("000001", "000002", "000003", "000004");
    }

    private AtomicInteger lockSecondChunk(Connection blocker, boolean releaseForRetry) throws SQLException {
        blocker.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        blocker.setAutoCommit(false);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            List<Object[]> rows = invocation.getArgument(1);
            Object[] firstRow = rows.getFirst();
            if ((long) firstRow[1] == 3L) {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    lockStagingRow(blocker, (long) firstRow[0]);
                }
                if (attempt == 3 && releaseForRetry) {
                    blocker.rollback();
                }
            }
            return invocation.callRealMethod();
        }).when(jdbc).batchUpdate(startsWith("INSERT INTO kis_stage"), anyList(), any(int[].class));
        return attempts;
    }

    private static void lockStagingRow(Connection blocker, long inputId) throws SQLException {
        try (var statement = blocker.prepareStatement(
                "SELECT original_row_no FROM kis_stage WHERE input_id = ? AND original_row_no = 3 FOR UPDATE")) {
            statement.setLong(1, inputId);
            statement.executeQuery().close();
        }
    }

    private static String row(String symbol) {
        return domestic(Market.KOSPI, symbol, "종목 " + symbol, "ST", ' ', 'N');
    }
}
