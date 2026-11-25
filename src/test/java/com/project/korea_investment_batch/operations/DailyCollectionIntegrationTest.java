package com.project.korea_investment_batch.operations;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import com.project.korea_investment_batch.support.KisFixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DailyCollectionIntegrationTest extends BatchIntegrationSupport {
    @Autowired DailyCollectionScheduler scheduler;
    @Autowired KisModule kis;
    @Autowired DartModule dart;
    @Autowired JobRepository jobs;

    @Test
    void continuesOtherMarketsAndReceiptDatesAndRetriesOnlyFailedKisInput() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        archive(Market.KOSPI, KisFixtures.domestic(Market.KOSPI, "000001", "기존 종목", "ST", ' ', 'N'));
        responses.put("/dart", request -> {
            if (request.query().get("corp_cls").equals("Y") && request.query().get("bgn_de").equals("20261120")) {
                return json("{\"status\":\"020\"}");
            }
            return page(request, 1, request.query().get("bgn_de").equals("20261120") ? 1 : 2);
        });
        scheduler.collect(today);
        var previous = kis.latest(Market.KOSPI).orElseThrow();
        assertThat(kis.input(run(Market.AMEX, today)).orElseThrow().state()).isEqualTo("FAILED");
        assertThat(dart.scanResult(run(Market.KOSPI, today)).orElseThrow().state()).isEqualTo("INCOMPLETE");
        assertThat(dart.disclosures(Market.KOSDAQ, today)).hasSize(1);
        assertThat(dart.latest(Market.KOSPI, today.minusDays(1)).orElseThrow().state()).isEqualTo("COMPLETED");

        archive(Market.AMEX, KisFixtures.american(Market.AMEX, "AAA", "2", ""));
        responses.put("/dart", request -> json("{\"status\":\"013\"}"));
        scheduler.collect(today);
        assertThat(kis.latest(Market.KOSPI)).contains(previous);
        assertThat(kis.latest(Market.AMEX)).isPresent();
        assertThat(dart.latest(Market.KOSPI, today).orElseThrow().scanRevision()).isEqualTo(1);
        assertThat(dart.latest(Market.KOSPI, today).orElseThrow().newCount()).isZero();
        assertThat(dart.latest(Market.KOSDAQ, today).orElseThrow().scanRevision()).isEqualTo(2);
        assertThat(dart.disclosures(Market.KOSDAQ, today)).hasSize(1);

        var kisExecution = kis.execution(run(Market.KOSPI, today)).orElseThrow();
        var kisParameters = jobs.getJobExecution(kisExecution.id()).getJobParameters();
        assertThat(kisParameters.getString("market")).isEqualTo("KOSPI");
        assertThat(kisParameters.getLocalDate("collectionDate")).isEqualTo(today);
        assertThat(kisParameters.getLong("inputRevision")).isEqualTo(1L);

        var dartExecution = dart.execution(new CollectionRun(Market.KOSDAQ, today, 2)).orElseThrow();
        var dartParameters = jobs.getJobExecution(dartExecution.id()).getJobParameters();
        assertThat(dartParameters.getString("market")).isEqualTo("KOSDAQ");
        assertThat(dartParameters.getLocalDate("receiptDate")).isEqualTo(today);
        assertThat(dartParameters.getLong("scanRevision")).isEqualTo(2L);
    }
}
