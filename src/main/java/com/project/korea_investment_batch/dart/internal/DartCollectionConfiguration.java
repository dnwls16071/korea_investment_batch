package com.project.korea_investment_batch.dart.internal;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class DartCollectionConfiguration {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
    private static final int LOOKBACK_DAYS = 2;

    @Bean
    DartProperties dartProperties(Environment environment) {
        String apiKey = environment.getProperty("DART_API_KEY", "");
        return new DartProperties(apiKey, DartProperties.MAX_PAGE_SIZE, REQUEST_TIMEOUT, RETRIES, RETRY_DELAY, LOOKBACK_DAYS);
    }
}
