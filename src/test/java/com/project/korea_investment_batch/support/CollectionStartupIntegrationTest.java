package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.dart.DartModule;
import com.project.korea_investment_batch.kis.KisModule;
import com.project.korea_investment_batch.shared.Market;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionStartupIntegrationTest extends DailyCollectionTestSupport {
    @Test
    void runsOneCollectionAtStartupWithoutProgramArguments() {
        dailyCollectionFixtures();

        try (var application = startApplication(Map.of("collection.run-on-startup", "true"))) {
            var kis = application.getBean(KisModule.class);
            var dart = application.getBean(DartModule.class);
            var date = kis.latest(Market.KOSPI).orElseThrow().collectionDate();
            for (Market market : Market.values()) {
                assertThat(kis.latest(market).orElseThrow().instrumentCount()).isEqualTo(1);
            }
            for (Market market : new Market[]{Market.KOSPI, Market.KOSDAQ}) {
                assertThat(dart.scanResult(run(market, date)).orElseThrow().state()).isEqualTo("COMPLETED");
                assertThat(dart.scanResult(run(market, date.minusDays(1))).orElseThrow().state()).isEqualTo("COMPLETED");
                assertThat(dart.latest(market, date).orElseThrow().scanRevision()).isEqualTo(1);
            }
            assertThat(requests).hasSize(9);
        }
    }
}
