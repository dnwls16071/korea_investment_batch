package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.KoreaInvestmentBatchApplication;
import com.project.korea_investment_batch.shared.Market;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class DailyCollectionTestSupport extends BatchIntegrationSupport {
    protected static ConfigurableApplicationContext startApplication(Map<String, String> overrides) {
        var properties = settings();
        properties.putAll(overrides);
        return new SpringApplicationBuilder(KoreaInvestmentBatchApplication.class, LocalCollectionConfiguration.class)
                .profiles("test")
                .initializers(context -> TestPropertyValues.of(properties).applyTo(context.getEnvironment()))
                .run();
    }

    protected static void dailyCollectionFixtures() {
        for (Market market : Market.values()) {
            String row = switch (market) {
                case KOSPI, KOSDAQ -> KisFixtures.domestic(market, "000001", "시험 종목", "ST", ' ', 'N');
                case NASDAQ, NYSE, AMEX -> KisFixtures.american(market, "AAA", "2", "");
            };
            archive(market, row);
        }
        responses.put("/dart", request -> json("{\"status\":\"013\"}"));
    }
}
