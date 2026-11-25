package com.project.korea_investment_batch.support;

import com.project.korea_investment_batch.dart.internal.DartProperties;
import com.project.korea_investment_batch.kis.internal.KisProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@TestConfiguration(proxyBeanMethods = false)
class LocalCollectionConfiguration {
    @Bean
    @Primary
    KisProperties testKisProperties(Environment environment) {
        Path archiveRoot = Path.of(environment.getRequiredProperty("test.kis.archive-root"));
        URI archiveBaseUrl = URI.create(environment.getRequiredProperty("test.kis.archive-base-url"));
        return new KisProperties(archiveRoot, 0.5, 2, Duration.ofSeconds(30), archiveBaseUrl);
    }

    @Bean
    @Primary
    DartProperties testDartProperties() {
        return new DartProperties("test-key", 2, Duration.ofSeconds(1), 1, Duration.ofMillis(10), 1);
    }

    @Bean
    @Primary
    @Qualifier("openDartEndpoint")
    URI localOpenDartEndpoint(Environment environment) {
        return URI.create(environment.getRequiredProperty("test.dart.endpoint"));
    }
}
