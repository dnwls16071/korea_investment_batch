package com.project.korea_investment_batch.kis.internal.job;

import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static com.project.korea_investment_batch.support.KisFixtures.american;
import static com.project.korea_investment_batch.support.KisFixtures.domestic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MarketMasterImportJobTest extends BatchIntegrationSupport {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final KisModule kis;

    @Autowired
    MarketMasterImportJobTest(KisModule kis) {
        this.kis = kis;
    }

    @Test
    void collectsFiveMarketsAndRestartsOnlyTheFailedMarket() {
        for (Market market : Market.values()) {
            if (market == Market.KOSPI || market == Market.KOSDAQ) {
                archive(market, Map.of("X-Source-Effective-Date", "2026-11-19"),
                        domestic(market, "000001", "보통주", "ST", ' ', 'N'),
                        domestic(market, "000002", "상장지수펀드", "EF", '2', 'N'),
                        domestic(market, "000003", "상장지수증권", "EN", '3', 'N'));
            } else {
                archive(market, american(market, "AAA", "2", ""), american(market, "BBB", "3", "001"),
                        american(market, "CCC", "3", "002"), american(market, "ZZZ", "3", "004"));
            }
        }
        var amex = responses.remove(archivePath(Market.AMEX));
        var first = kis.collect(DATE);
        assertThat(first.get(Market.AMEX).state()).isEqualTo(BatchStatus.FAILED);
        assertThat(kis.latest(Market.AMEX)).isEmpty();
        assertThat(kis.input(run(Market.AMEX, DATE)).orElseThrow().failureStep()).isEqualTo("downloadArchive");
        assertThat(first.values()).filteredOn(result -> result.state() == BatchStatus.COMPLETED).hasSize(4);

        responses.put(archivePath(Market.AMEX), amex);
        requests.clear();
        var second = kis.collect(DATE);
        assertThat(requests).extracting(Request::path).containsExactly("/kis/amsmst.cod.zip");
        assertThat(second.values()).allMatch(result -> result.state() == BatchStatus.COMPLETED);
        for (Market market : Market.values()) {
            assertThat(kis.instruments(market)).extracting(KisModule.Instrument::instrumentType)
                    .containsExactly("STOCK", "ETF", "ETN");
        }
        assertThat(kis.latest(Market.KOSPI).orElseThrow().effectiveDate()).isEqualTo(DATE.minusDays(1));
        assertThat(kis.latest(Market.KOSPI).orElseThrow().collectedAt()).isNotNull();
        assertThat(kis.latest(Market.AMEX).orElseThrow().effectiveDate()).isNull();
        assertThat(kis.instruments(Market.KOSPI))
                .extracting(KisModule.Instrument::securityCode, KisModule.Instrument::productCode)
                .containsExactly(tuple("ST", ""), tuple("EF", "2"), tuple("EN", "3"));
        assertThat(kis.instruments(Market.NYSE))
                .extracting(KisModule.Instrument::securityCode, KisModule.Instrument::productCode)
                .containsExactly(tuple("2", ""), tuple("3", "001"), tuple("3", "002"));
        assertThat(kis.execution(run(Market.NYSE, DATE)).orElseThrow().steps())
                .filteredOn(step -> step.name().equals("stageInstruments"))
                .singleElement().satisfies(step -> {
                    assertThat(step.reads()).isEqualTo(4);
                    assertThat(step.filtered()).isEqualTo(1);
                    assertThat(step.writes()).isEqualTo(3);
                });
    }

    @Test
    void keepsLastGoodSnapshotAcrossInvalidFiles() {
        var market = Market.KOSPI;
        String one = domestic(market, "000001", "한글", "ST", ' ', 'N');
        archive(market, one, domestic(market, "000002", "둘", "ST", ' ', 'N'),
                domestic(market, "000003", "셋", "ST", ' ', 'N'));
        assertThat(kis.resume(run(market, DATE), false).status()).isEqualTo(BatchStatus.COMPLETED);
        var previous = kis.latest(market).orElseThrow();

        responses.put(archivePath(market), ignored -> json("invalid ZIP"));
        failed(market, DATE.plusDays(1), "downloadArchive", previous);
        archive(market, one, one);
        failed(market, DATE.plusDays(2), "validateSnapshot", previous);
        archive(market);
        failed(market, DATE.plusDays(3), "validateSnapshot", previous);
        archive(market, one);
        failed(market, DATE.plusDays(4), "validateSnapshot", previous);
        archive(market, domestic(market, "000004", "", "ST", ' ', 'N'));
        failed(market, DATE.plusDays(5), "stageInstruments", previous);
    }

    private void failed(Market market, LocalDate date, String step, KisModule.Snapshot previous) {
        var run = run(market, date);
        assertThat(kis.resume(run, false).status()).isEqualTo(BatchStatus.FAILED);
        assertThat(kis.input(run).orElseThrow().failureStep()).isEqualTo(step);
        assertThat(kis.latest(market)).contains(previous);
        assertThat(kis.changes(market, date)).isEmpty();
    }
}
