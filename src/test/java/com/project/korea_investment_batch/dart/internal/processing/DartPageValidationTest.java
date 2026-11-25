package com.project.korea_investment_batch.dart.internal.processing;

import com.project.korea_investment_batch.dart.remote.OpenDartClient;
import com.project.korea_investment_batch.shared.CollectionRun;
import com.project.korea_investment_batch.shared.Market;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DartPageValidationTest {
    private final CollectionRun run = new CollectionRun(Market.KOSPI, LocalDate.of(2026, 11, 24), 1);

    @Test
    void rejectsDuplicateAcrossPagesEvenWhenReportedTotalsAreStable() {
        DartPageValidation scan = new DartPageValidation(run, 2);
        scan.accept(1, page(1, "01", "02"));
        assertThatThrownBy(() -> scan.accept(2, page(2, "02", "03")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsMissingReceiptWithNoDuplicate() {
        DartPageValidation scan = new DartPageValidation(run, 2);
        scan.accept(1, page(1, "01", "02"));
        assertThatThrownBy(() -> {
            scan.accept(2, page(2, "03"));
            scan.verifyComplete();
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("count");
    }

    private OpenDartClient.Page page(int page, String... endings) {
        return new OpenDartClient.Page("000", page, 2, 2, 4,
                java.util.Arrays.stream(endings).map(ending ->
                        new OpenDartClient.Receipt("202611240000" + ending, "20261124", "Y", "000001", "회사", "보고서"))
                        .toList());
    }
}
