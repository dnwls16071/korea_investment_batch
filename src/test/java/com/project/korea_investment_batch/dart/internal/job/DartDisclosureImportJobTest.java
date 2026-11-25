package com.project.korea_investment_batch.dart.internal.job;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.shared.Market;
import com.project.korea_investment_batch.support.BatchIntegrationSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class DartDisclosureImportJobTest extends BatchIntegrationSupport {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private final DartModule dart;

    @Autowired
    DartDisclosureImportJobTest(DartModule dart) {
        this.dart = dart;
    }

    @Test
    void collectsBothMarketsIncludesCorrectionAndCountsOnlyNewReceiptsInNewRevisions() {
        responses.put("/dart", request -> {
            var query = request.query();
            assertThat(query).containsEntry("crtfc_key", "test-key")
                    .containsEntry("bgn_de", "20261120").containsEntry("end_de", "20261120")
                    .containsEntry("last_reprt_at", "N").containsEntry("pblntf_ty", "B")
                    .containsEntry("sort", "date").containsEntry("sort_mth", "desc");
            boolean kospi = query.get("corp_cls").equals("Y");
            int offset = kospi ? 100 : 200;
            int number = Integer.parseInt(query.get("page_no"));
            return number == 1 ? page(request, 3, offset + 1, offset + 2) : page(request, 3, offset + 3);
        });
        for (var market : new Market[]{Market.KOSPI, Market.KOSDAQ}) {
            assertThat(dart.scan(market, DATE).status()).isEqualTo(BatchStatus.COMPLETED);
            int offset = market == Market.KOSPI ? 100 : 200;
            assertThat(dart.disclosures(market, DATE)).extracting(DartModule.Disclosure::receiptNumber)
                    .containsExactly(id(offset + 1), id(offset + 2), id(offset + 3));
            var correction = dart.disclosures(market, DATE).get(1);
            assertThat(correction.stockCode()).isEqualTo("000999");
            assertThat(correction.title()).startsWith("[정정]");
            assertThat(correction.originalLink()).endsWith("rcpNo=" + id(offset + 2));
            assertThat(dart.latest(market, DATE).orElseThrow().newCount()).isEqualTo(3);
        }
        assertThat(dart.scan(Market.KOSPI, DATE).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(dart.latest(Market.KOSPI, DATE).orElseThrow().scanRevision()).isEqualTo(2);
        assertThat(dart.latest(Market.KOSPI, DATE).orElseThrow().newCount()).isZero();

        responses.put("/dart", request -> request.query().get("page_no").equals("1")
                ? page(request, 4, 101, 102) : page(request, 4, 103, 104));
        assertThat(dart.scan(Market.KOSPI, DATE).status()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(dart.latest(Market.KOSPI, DATE).orElseThrow().newCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) - COUNT(DISTINCT rcept_no) FROM dart_disclosure", Integer.class))
                .isZero();
    }
}
