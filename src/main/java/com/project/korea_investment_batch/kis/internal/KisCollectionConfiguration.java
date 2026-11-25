package com.project.korea_investment_batch.kis.internal;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class KisCollectionConfiguration {
    private static final double MINIMUM_RETAINED_RATIO = 0.9;
    private static final int CHUNK_SIZE = 500;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final URI ARCHIVE_BASE_URL = URI.create("https://new.real.download.dws.co.kr/common/master/");

    @Bean
    KisProperties kisProperties(Environment environment) {
        Path archiveRoot = Path.of(environment.getProperty("KIS_ARCHIVE_ROOT", "./data/kis"));
        return new KisProperties(archiveRoot, MINIMUM_RETAINED_RATIO, CHUNK_SIZE, DOWNLOAD_TIMEOUT, ARCHIVE_BASE_URL);
    }
}
